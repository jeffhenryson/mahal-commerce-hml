package com.cernecommerce.core.domain.exception.estoque;

/** 400 PROMOTION_INVALID_PERIOD: {@code endsAt <= startsAt}. */
public class InvalidPromotionPeriodException extends RuntimeException {
    public InvalidPromotionPeriodException() {
        super("O fim da promoção deve ser depois do início");
    }
}
