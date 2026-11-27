package com.cernecommerce.core.domain.exception.estoque;

/** {@code link-sku} com SKU que não existe no catálogo (produto ou variação) — 404 {@code SKU_NOT_FOUND}. */
public class ProductRequestSkuNotFoundException extends RuntimeException {
    public ProductRequestSkuNotFoundException(String sku) {
        super("SKU não encontrado no catálogo: " + sku);
    }
}
