package com.cernecommerce.core.domain.exception.assistente;

/** Confirmar depois da validade da ação (409 ACAO_EXPIRADA). */
public class AcaoExpiradaException extends RuntimeException {
    public AcaoExpiradaException() {
        super("Esta ação expirou. Peça de novo ao assistente.");
    }
}
