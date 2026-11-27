package com.cernecommerce.core.domain.exception.estoque;

/** Regra de formato que o Bean Validation não cobre (telefone, data no passado) — 400 {@code VALIDATION_ERROR}. */
public class ProductRequestValidationException extends RuntimeException {
    public ProductRequestValidationException(String message) {
        super(message);
    }
}
