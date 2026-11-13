package com.cernecommerce.core.domain.model.storage;

/**
 * Resultado de {@code ImageCompressorPort#compress}: os bytes que vão de fato para o storage e o
 * formato deles. O formato pode diferir do enviado — um PNG sem transparência sai como JPEG —, e
 * é por isso que a extensão gravada tem que vir daqui, não da detecção feita antes da compressão.
 */
public record CompressedImage(byte[] bytes, ImageFormat format) {
}
