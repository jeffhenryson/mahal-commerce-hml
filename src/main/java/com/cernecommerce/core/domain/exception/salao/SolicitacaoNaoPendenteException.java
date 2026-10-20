package com.cernecommerce.core.domain.exception.salao;

/** A solicitação já foi aceita, recusada ou expirou. */
public class SolicitacaoNaoPendenteException extends RuntimeException {
    public SolicitacaoNaoPendenteException(String message) {
        super(message);
    }
}
