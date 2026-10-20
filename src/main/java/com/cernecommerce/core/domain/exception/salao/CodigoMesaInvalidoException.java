package com.cernecommerce.core.domain.exception.salao;

/** O código digitado não é o da mesa ocupada (ou a mesa já foi encerrada). */
public class CodigoMesaInvalidoException extends RuntimeException {
    public CodigoMesaInvalidoException(String message) {
        super(message);
    }
}
