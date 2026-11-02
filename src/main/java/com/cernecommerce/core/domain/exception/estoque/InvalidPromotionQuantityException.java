package com.cernecommerce.core.domain.exception.estoque;

/** 400 PROMOTION_INVALID_QUANTITY: grupo com menos de 2 unidades não é promoção por quantidade. */
public class InvalidPromotionQuantityException extends RuntimeException {
    public InvalidPromotionQuantityException(int quantity) {
        super("A promoção precisa de pelo menos 2 unidades por grupo: " + quantity);
    }
}
