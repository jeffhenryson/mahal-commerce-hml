package com.cernecommerce.core.domain.exception.estoque;

/** 400 PROMOTION_INVALID_PRICE. */
public class InvalidPromotionPriceException extends RuntimeException {
    public InvalidPromotionPriceException(java.math.BigDecimal price) {
        super("O preço do grupo deve ser maior que zero: " + price);
    }
}
