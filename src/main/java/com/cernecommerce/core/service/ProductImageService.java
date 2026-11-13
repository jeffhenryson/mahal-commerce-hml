package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.storage.ImageTooLargeException;
import com.cernecommerce.core.domain.exception.storage.InvalidImageFormatException;
import com.cernecommerce.core.domain.model.storage.CompressedImage;
import com.cernecommerce.core.domain.model.storage.FileServeResult;
import com.cernecommerce.core.domain.model.storage.ImageFormat;
import com.cernecommerce.core.ports.in.ProductImageUseCase;
import com.cernecommerce.core.ports.out.storage.ImageCompressorPort;
import com.cernecommerce.core.ports.out.storage.ProductImageStoragePort;

import java.io.IOException;
import java.io.InputStream;

/**
 * Espelha {@code AvatarService} na validação e no armazenamento, sem a parte de vincular o
 * arquivo a uma entidade — imagem de produto não tem dono no banco (ver
 * {@link ProductImageUseCase}). Por isso também não há {@code @Transactional} aqui: nada é
 * escrito em banco, e abrir transação para uma operação puramente de I/O de arquivo só seguraria
 * conexão do pool durante o upload.
 */
public class ProductImageService implements ProductImageUseCase {

    private final ProductImageStoragePort storagePort;
    private final ImageCompressorPort compressor;
    private final long maxSizeBytes;
    private final int maxDimension;
    private final String baseUrl;

    public ProductImageService(ProductImageStoragePort storagePort, ImageCompressorPort compressor,
                               long maxSizeBytes, int maxDimension, String baseUrl) {
        this.storagePort = storagePort;
        this.compressor = compressor;
        this.maxSizeBytes = maxSizeBytes;
        this.maxDimension = maxDimension;
        this.baseUrl = baseUrl;
    }

    @Override
    public String upload(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new InvalidImageFormatException();
        if (bytes.length > maxSizeBytes) throw new ImageTooLargeException(maxSizeBytes);

        // Detecta pelo conteúdo, nunca pela extensão do arquivo enviado — ver ImageFormat.
        ImageFormat format = ImageFormat.detect(bytes)
                .orElseThrow(InvalidImageFormatException::new);

        // maxSizeBytes limita a ENTRADA; o que vai para o disco é a versão reduzida. A extensão
        // vem do resultado, porque PNG sem transparência sai como JPEG.
        CompressedImage image = compressor.compress(bytes, format, maxDimension);
        String filename = storagePort.save(image.bytes(), image.format().extension());

        // Com S3/CDN a URL é a do bucket; com armazenamento local não existe URL pública e quem
        // serve os bytes é a própria API, em GET /product-images/{filename}.
        return storagePort.getPublicUrl(filename)
                .orElse(baseUrl + "/product-images/" + filename);
    }

    @Override
    public FileServeResult serve(String filename) {
        java.util.Optional<String> publicUrl = storagePort.getPublicUrl(filename);
        if (publicUrl.isPresent()) {
            return new FileServeResult.Redirect(publicUrl.get());
        }
        return storagePort.load(filename).map(stream -> {
            try (InputStream is = stream) {
                String ext = filename.contains(".")
                        ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                        : "";
                return (FileServeResult) new FileServeResult.LocalFile(is.readAllBytes(), ext);
            } catch (IOException e) {
                return (FileServeResult) new FileServeResult.NotFound();
            }
        }).orElse(new FileServeResult.NotFound());
    }
}
