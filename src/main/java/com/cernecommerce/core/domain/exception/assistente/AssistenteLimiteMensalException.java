package com.cernecommerce.core.domain.exception.assistente;

/** A loja passou do limite mensal de tokens do assistente (409 ASSISTENTE_LIMITE_MENSAL, antes do stream). */
public class AssistenteLimiteMensalException extends RuntimeException {
    public AssistenteLimiteMensalException(String message) {
        super(message);
    }
}
