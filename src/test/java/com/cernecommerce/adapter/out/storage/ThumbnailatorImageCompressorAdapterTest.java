package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.domain.exception.storage.ImageDimensionsTooLargeException;
import com.cernecommerce.core.domain.model.storage.CompressedImage;
import com.cernecommerce.core.domain.model.storage.ImageFormat;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailatorImageCompressorAdapterTest {

    private final ThumbnailatorImageCompressorAdapter adapter = new ThumbnailatorImageCompressorAdapter();

    /** Ruído colorido: não comprime bem, então simula o peso de uma foto real. */
    private static byte[] encode(int width, int height, int type, String format) throws IOException {
        BufferedImage img = new BufferedImage(width, height, type);
        Random rnd = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = type == BufferedImage.TYPE_INT_ARGB ? (x % 2 == 0 ? 0x80 : 0xFF) : 0xFF;
                img.setRGB(x, y, (alpha << 24) | rnd.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void foto_grande_e_reduzida_para_o_maior_lado_e_fica_menor_em_bytes() throws IOException {
        byte[] original = encode(3000, 2000, BufferedImage.TYPE_INT_RGB, "jpg");

        CompressedImage result = adapter.compress(original, ImageFormat.JPEG, 1200);

        BufferedImage out = decode(result.bytes());
        assertThat(result.format()).isEqualTo(ImageFormat.JPEG);
        assertThat(out.getWidth()).isEqualTo(1200);
        assertThat(out.getHeight()).isEqualTo(800); // proporção mantida
        assertThat(result.bytes().length).isLessThan(original.length);
    }

    @Test
    void imagem_pequena_volta_identica_sem_reencodar() throws IOException {
        byte[] original = encode(200, 150, BufferedImage.TYPE_INT_RGB, "jpg");

        CompressedImage result = adapter.compress(original, ImageFormat.JPEG, 1200);

        assertThat(result.bytes()).isSameAs(original);
        assertThat(result.format()).isEqualTo(ImageFormat.JPEG);
    }

    @Test
    void png_com_transparencia_continua_png_com_alpha() throws IOException {
        byte[] original = encode(1600, 1600, BufferedImage.TYPE_INT_ARGB, "png");

        CompressedImage result = adapter.compress(original, ImageFormat.PNG, 400);

        assertThat(result.format()).isEqualTo(ImageFormat.PNG);
        BufferedImage out = decode(result.bytes());
        assertThat(out.getWidth()).isEqualTo(400);
        assertThat(out.getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void png_sem_transparencia_grande_vira_jpeg() throws IOException {
        byte[] original = encode(1600, 1200, BufferedImage.TYPE_INT_RGB, "png");

        CompressedImage result = adapter.compress(original, ImageFormat.PNG, 1200);

        assertThat(result.format()).isEqualTo(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(result.bytes())).contains(ImageFormat.JPEG);
    }

    @Test
    void webp_passa_direto_porque_o_jdk_nao_le_webp() {
        byte[] webp = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 0};

        CompressedImage result = adapter.compress(webp, ImageFormat.WEBP, 1200);

        assertThat(result.bytes()).isSameAs(webp);
    }

    @Test
    void conteudo_que_nao_decodifica_volta_como_veio_sem_derrubar_o_upload() {
        byte[] soCabecalho = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};

        CompressedImage result = adapter.compress(soCabecalho, ImageFormat.JPEG, 1200);

        assertThat(result.bytes()).isSameAs(soCabecalho);
    }

    @Test
    void dimensao_absurda_e_recusada_antes_de_decodificar() throws IOException {
        // PNG de cor única 8000x8000 = 64 MP: pesa poucos KB no disco, mas decodificado
        // ocuparia ~256 MB de heap. É exatamente o formato de um decompression bomb.
        BufferedImage img = new BufferedImage(8000, 8000, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);

        assertThatThrownBy(() -> adapter.compress(out.toByteArray(), ImageFormat.PNG, 1200))
                .isInstanceOf(ImageDimensionsTooLargeException.class);
    }
}
