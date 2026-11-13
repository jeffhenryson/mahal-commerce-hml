package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.storage.ImageTooLargeException;
import com.cernecommerce.core.domain.exception.storage.InvalidImageFormatException;
import com.cernecommerce.core.domain.model.storage.CompressedImage;
import com.cernecommerce.core.domain.model.storage.FileServeResult;
import com.cernecommerce.core.domain.model.storage.ImageFormat;
import com.cernecommerce.core.ports.out.storage.ImageCompressorPort;
import com.cernecommerce.core.ports.out.storage.ProductImageStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductImageServiceTest {

    private static final long MAX_SIZE = 1024L;
    private static final String BASE_URL = "https://api.mahal.test";

    private static final int MAX_DIMENSION = 1200;
    /** Compressor que devolve o que recebeu — os testes de formato/limite não dependem dele. */
    private static final ImageCompressorPort PASS_THROUGH = (b, f, d) -> new CompressedImage(b, f);

    private ProductImageStoragePort storagePort;
    private ProductImageService service;

    @BeforeEach
    void setup() {
        storagePort = mock(ProductImageStoragePort.class);
        service = new ProductImageService(storagePort, PASS_THROUGH, MAX_SIZE, MAX_DIMENSION, BASE_URL);
    }

    private static byte[] jpeg(int totalLength) {
        byte[] bytes = new byte[totalLength];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    // --- upload ---

    @Test
    void upload_grava_com_a_extensao_detectada_e_devolve_url_local() {
        when(storagePort.save(any(), eq("jpg"))).thenReturn("abc.jpg");
        when(storagePort.getPublicUrl("abc.jpg")).thenReturn(Optional.empty());

        String url = service.upload(jpeg(64));

        // Sem URL pública (storage local), quem serve os bytes é a própria API.
        assertEquals(BASE_URL + "/product-images/abc.jpg", url);
        verify(storagePort).save(any(), eq("jpg"));
    }

    @Test
    void upload_com_url_publica_devolve_a_do_storage_sem_passar_pela_api() {
        when(storagePort.save(any(), anyString())).thenReturn("abc.jpg");
        when(storagePort.getPublicUrl("abc.jpg"))
                .thenReturn(Optional.of("https://cdn.mahal.test/product-images/abc.jpg"));

        assertEquals("https://cdn.mahal.test/product-images/abc.jpg", service.upload(jpeg(64)));
    }

    @Test
    void upload_detecta_o_formato_pelo_conteudo_e_nao_pelo_nome_do_arquivo() {
        // PNG de verdade: precisa gravar .png mesmo que o cliente tivesse chamado de .jpg.
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        when(storagePort.save(any(), eq("png"))).thenReturn("abc.png");
        when(storagePort.getPublicUrl("abc.png")).thenReturn(Optional.empty());

        service.upload(png);

        verify(storagePort).save(any(), eq("png"));
    }

    @Test
    void upload_de_conteudo_que_nao_e_imagem_e_recusado_sem_gravar_nada() {
        assertThrows(InvalidImageFormatException.class,
                () -> service.upload("<?php system($_GET[0]); ?>".getBytes()));
        verifyNoInteractions(storagePort);
    }

    @Test
    void upload_vazio_ou_nulo_e_recusado_sem_gravar_nada() {
        assertThrows(InvalidImageFormatException.class, () -> service.upload(new byte[0]));
        assertThrows(InvalidImageFormatException.class, () -> service.upload(null));
        verifyNoInteractions(storagePort);
    }

    @Test
    void upload_acima_do_limite_e_recusado_antes_de_validar_o_formato() {
        // O tamanho é checado primeiro de propósito: não faz sentido varrer magic bytes de um
        // arquivo que já está reprovado, e a mensagem útil ao operador é a do tamanho.
        assertThrows(ImageTooLargeException.class, () -> service.upload(jpeg((int) MAX_SIZE + 1)));
        verifyNoInteractions(storagePort);
    }

    @Test
    void upload_exatamente_no_limite_e_aceito() {
        when(storagePort.save(any(), anyString())).thenReturn("abc.jpg");
        when(storagePort.getPublicUrl(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.upload(jpeg((int) MAX_SIZE)));
    }

    @Test
    void mensagem_do_limite_sai_em_mb_para_o_operador() {
        ProductImageService bigger = new ProductImageService(storagePort, PASS_THROUGH, 5_242_880L,
                MAX_DIMENSION, BASE_URL);
        ImageTooLargeException ex = assertThrows(ImageTooLargeException.class,
                () -> bigger.upload(jpeg(5_242_881)));
        assertTrue(ex.getMessage().contains("5 MB"), ex.getMessage());
    }

    @Test
    void upload_grava_a_saida_do_compressor_com_a_extensao_dela() {
        // PNG sem transparência vira JPEG no compressor: o arquivo gravado tem que ser .jpg com
        // os bytes reduzidos, não o PNG original com a extensão detectada na entrada.
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        byte[] reduzido = jpeg(16);
        ImageCompressorPort compressor = mock(ImageCompressorPort.class);
        when(compressor.compress(png, ImageFormat.PNG, MAX_DIMENSION))
                .thenReturn(new CompressedImage(reduzido, ImageFormat.JPEG));
        when(storagePort.save(reduzido, "jpg")).thenReturn("abc.jpg");
        when(storagePort.getPublicUrl("abc.jpg")).thenReturn(Optional.empty());
        ProductImageService comCompressor =
                new ProductImageService(storagePort, compressor, MAX_SIZE, MAX_DIMENSION, BASE_URL);

        assertEquals(BASE_URL + "/product-images/abc.jpg", comCompressor.upload(png));
        verify(storagePort).save(reduzido, "jpg");
    }

    // --- serve ---

    @Test
    void serve_com_url_publica_manda_redirecionar() {
        when(storagePort.getPublicUrl("abc.jpg"))
                .thenReturn(Optional.of("https://cdn.mahal.test/product-images/abc.jpg"));

        FileServeResult result = service.serve("abc.jpg");

        assertInstanceOf(FileServeResult.Redirect.class, result);
        assertEquals("https://cdn.mahal.test/product-images/abc.jpg",
                ((FileServeResult.Redirect) result).url());
        // Com CDN o byte nunca passa pela aplicação.
        verify(storagePort, never()).load(anyString());
    }

    @Test
    void serve_local_devolve_os_bytes_com_a_extensao_do_filename() {
        when(storagePort.getPublicUrl("abc.png")).thenReturn(Optional.empty());
        when(storagePort.load("abc.png")).thenReturn(Optional.of(new ByteArrayInputStream(new byte[] {1, 2, 3})));

        FileServeResult result = service.serve("abc.png");

        assertInstanceOf(FileServeResult.LocalFile.class, result);
        FileServeResult.LocalFile file = (FileServeResult.LocalFile) result;
        assertArrayEquals(new byte[] {1, 2, 3}, file.bytes());
        assertEquals("png", file.extension());
    }

    @Test
    void serve_de_arquivo_inexistente_volta_not_found() {
        when(storagePort.getPublicUrl("sumiu.jpg")).thenReturn(Optional.empty());
        when(storagePort.load("sumiu.jpg")).thenReturn(Optional.empty());

        assertInstanceOf(FileServeResult.NotFound.class, service.serve("sumiu.jpg"));
    }

    @Test
    void serve_de_filename_sem_extensao_nao_estoura() {
        when(storagePort.getPublicUrl("semponto")).thenReturn(Optional.empty());
        when(storagePort.load("semponto")).thenReturn(Optional.of(new ByteArrayInputStream(new byte[] {9})));

        FileServeResult result = service.serve("semponto");

        assertEquals("", ((FileServeResult.LocalFile) result).extension());
    }
}
