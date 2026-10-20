package com.cernecommerce.core.domain.exception.salao;

/** Cadastro pelo QR com CPF ou celular de cliente existente — o lead deve se identificar. */
public class ClienteJaCadastradoException extends RuntimeException {
    public ClienteJaCadastradoException(String message) {
        super(message);
    }
}
