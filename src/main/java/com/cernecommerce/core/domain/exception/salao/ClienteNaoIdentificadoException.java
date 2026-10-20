package com.cernecommerce.core.domain.exception.salao;

/** CPF ou celular informado no QR não corresponde a nenhum cliente. */
public class ClienteNaoIdentificadoException extends RuntimeException {
    public ClienteNaoIdentificadoException(String message) {
        super(message);
    }
}
