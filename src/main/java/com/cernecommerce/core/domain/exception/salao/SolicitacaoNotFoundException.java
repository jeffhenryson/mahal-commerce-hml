package com.cernecommerce.core.domain.exception.salao;

/** Solicitação de sessão inexistente. */
public class SolicitacaoNotFoundException extends RuntimeException {
    public SolicitacaoNotFoundException(String message) {
        super(message);
    }
}
