package com.cernecommerce.core.domain.exception.estoque;

/** 404 PROMOTION_NOT_FOUND. */
public class QuantityPromotionNotFoundException extends RuntimeException {
    public QuantityPromotionNotFoundException(Long id) {
        super("Promoção não encontrada: " + id);
    }
}
