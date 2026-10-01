package com.cernecommerce.core.domain.exception.recebivel;

public class ReceivableNotFoundException extends RuntimeException {

    public ReceivableNotFoundException(Long id) {
        super("Marcado não encontrado: " + id);
    }
}
