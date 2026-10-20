package com.cernecommerce.core.domain.exception.salao;

/** O cliente já tem uma solicitação pendente nesta mesa — evita pedido em dobro pelo QR. */
public class SolicitacaoJaPendenteException extends RuntimeException {
    public SolicitacaoJaPendenteException(String message) {
        super(message);
    }
}
