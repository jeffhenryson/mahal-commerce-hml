package com.cernecommerce.core.domain.exception.salao;

/** Chamado da mesa inexistente. */
public class ChamadoNotFoundException extends RuntimeException {
    public ChamadoNotFoundException(String message) {
        super(message);
    }
}
