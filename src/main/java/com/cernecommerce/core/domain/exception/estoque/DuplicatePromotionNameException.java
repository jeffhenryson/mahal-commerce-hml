package com.cernecommerce.core.domain.exception.estoque;

/** 409 DUPLICATE_PROMOTION_NAME (comparação sem diferenciar maiúsculas). */
public class DuplicatePromotionNameException extends RuntimeException {
    public DuplicatePromotionNameException(String name) {
        super("Já existe uma promoção com o nome: " + name);
    }
}
