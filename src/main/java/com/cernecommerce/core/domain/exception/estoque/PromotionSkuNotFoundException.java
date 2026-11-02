package com.cernecommerce.core.domain.exception.estoque;

/** 404 PROMOTION_SKU_NOT_FOUND. */
public class PromotionSkuNotFoundException extends RuntimeException {
    public PromotionSkuNotFoundException(String sku) {
        super("SKU não encontrado: " + sku);
    }
}
