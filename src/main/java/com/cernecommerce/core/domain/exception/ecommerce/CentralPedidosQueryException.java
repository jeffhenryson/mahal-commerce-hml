package com.cernecommerce.core.domain.exception.ecommerce;

/** Consulta inválida da fila por coluna (pendência 10 §1): INVALID_COLUMN, FROM_REQUIRED ou INVALID_PAGE_SIZE. */
public class CentralPedidosQueryException extends RuntimeException {

    private final String code;

    public CentralPedidosQueryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
