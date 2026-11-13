package com.cernecommerce.core.domain.exception.storage;

public class ImageDimensionsTooLargeException extends RuntimeException {
    public ImageDimensionsTooLargeException(long maxPixels) {
        super("Imagem com resolução acima do permitido (" + (maxPixels / 1_000_000) + " megapixels)");
    }
}
