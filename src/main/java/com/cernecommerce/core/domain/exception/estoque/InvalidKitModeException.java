package com.cernecommerce.core.domain.exception.estoque;

/** {@code kitMode} sem {@code type = KIT}, ou tentativa de trocar o modo/tipo de um kit pré-montado → 422 {@code KIT_MODE_INVALID}. */
public class InvalidKitModeException extends RuntimeException {
    public InvalidKitModeException(String message) {
        super(message);
    }
}
