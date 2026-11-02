package com.cernecommerce.core.domain.exception.estoque;

/** 409 PROMOTION_SKU_INACTIVE: inativo, arquivado, rascunho, kit ou SKU base que não vende. */
public class PromotionSkuInactiveException extends RuntimeException {
    public PromotionSkuInactiveException(String sku) {
        super("SKU inativo, arquivado ou que não é vendido: " + sku);
    }
}
