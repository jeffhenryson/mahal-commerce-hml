package com.cernecommerce.core.domain.exception.salao;

/** Mesa de autoatendimento inexistente, ou QR (token) inválido ou de mesa desativada. */
public class SalaoMesaNotFoundException extends RuntimeException {
    public SalaoMesaNotFoundException(String message) {
        super(message);
    }
}
