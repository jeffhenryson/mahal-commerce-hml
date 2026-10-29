package com.cernecommerce.core.domain.exception.ecommerce;

/** Recusa sem motivo, com nota longa demais, ou {@code OUTRO} sem nota (ECM-F012). */
public class RecusaInvalidaException extends RuntimeException {

    public RecusaInvalidaException(String message) {
        super(message);
    }
}
