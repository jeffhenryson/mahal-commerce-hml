package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.domain.exception.storage.ImageDimensionsTooLargeException;
import com.cernecommerce.core.domain.model.storage.CompressedImage;
import com.cernecommerce.core.domain.model.storage.ImageFormat;
import com.cernecommerce.core.ports.out.storage.ImageCompressorPort;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Compressão com Thumbnailator. A escolha da lib é pela orientação EXIF: foto de celular guarda
 * a rotação só no metadado, e reencodar com ImageIO puro grava a imagem deitada. O Thumbnailator
 * aplica a rotação ao decodificar.
 *
 * <p>Regras, na ordem:</p>
 * <ol>
 *   <li>WebP passa direto — o ImageIO do JDK não lê WebP. Na prática ele chega do frontend já
 *       pequeno, e o limite de bytes do serviço vale de qualquer jeito.</li>
 *   <li>Dimensões lidas do cabeçalho, sem decodificar; acima de {@link #MAX_PIXELS} é recusado
 *       antes de alocar o bitmap (decompression bomb).</li>
 *   <li>Já dentro de {@code maxDimension} e até {@link #SMALL_ENOUGH_BYTES}: passa direto, sem
 *       perda dupla de qualidade no que o frontend já comprimiu.</li>
 *   <li>Com transparência: redimensiona e mantém PNG. Sem: redimensiona e grava JPEG
 *       {@link #JPEG_QUALITY}.</li>
 *   <li>Se o resultado sair maior que o original, fica o original.</li>
 * </ol>
 * Falha de decodificação (JPEG CMYK, arquivo truncado) não derruba o upload: loga e devolve o
 * original, que é o comportamento de antes desta classe existir.
 */
public class ThumbnailatorImageCompressorAdapter implements ImageCompressorPort {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailatorImageCompressorAdapter.class);

    /** ~160 MB de heap no pior caso (4 bytes/pixel). Cobre câmera de celular de 12–40 MP. */
    static final long MAX_PIXELS = 40_000_000L;
    static final int SMALL_ENOUGH_BYTES = 300 * 1024;
    static final double JPEG_QUALITY = 0.80;

    @Override
    public CompressedImage compress(byte[] bytes, ImageFormat format, int maxDimension) {
        CompressedImage original = new CompressedImage(bytes, format);
        if (format == ImageFormat.WEBP) return original;

        Header header;
        try {
            header = readHeader(bytes);
        } catch (IOException | RuntimeException e) {
            log.warn("image.compress.header_unreadable format={} bytes={}", format, bytes.length, e);
            return original;
        }
        if (header == null) return original;

        if ((long) header.width() * header.height() > MAX_PIXELS) {
            throw new ImageDimensionsTooLargeException(MAX_PIXELS);
        }

        int longestSide = Math.max(header.width(), header.height());
        if (longestSide <= maxDimension && bytes.length <= SMALL_ENOUGH_BYTES) return original;

        ImageFormat outFormat = header.hasAlpha() ? ImageFormat.PNG : ImageFormat.JPEG;
        byte[] out;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Thumbnails.Builder<?> builder = Thumbnails.of(new ByteArrayInputStream(bytes))
                    .outputFormat(outFormat == ImageFormat.PNG ? "png" : "jpg");
            if (longestSide > maxDimension) {
                builder.size(maxDimension, maxDimension); // mantém proporção
            } else {
                builder.scale(1.0);
            }
            if (outFormat == ImageFormat.JPEG) builder.outputQuality(JPEG_QUALITY);
            builder.toOutputStream(baos);
            out = baos.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.warn("image.compress.failed format={} bytes={} {}x{}",
                    format, bytes.length, header.width(), header.height(), e);
            return original;
        }

        if (out.length >= bytes.length) return original;
        log.info("image.compress.ok {}x{} {}KB -> {}KB ({})", header.width(), header.height(),
                bytes.length / 1024, out.length / 1024, outFormat);
        return new CompressedImage(out, outFormat);
    }

    private record Header(int width, int height, boolean hasAlpha) {}

    /** Lê só o cabeçalho; {@code null} quando nenhum reader do ImageIO reconhece o conteúdo. */
    private static Header readHeader(byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                boolean hasAlpha = false;
                Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
                if (types.hasNext()) hasAlpha = types.next().getColorModel().hasAlpha();
                return new Header(width, height, hasAlpha);
            } finally {
                reader.dispose();
            }
        }
    }
}
