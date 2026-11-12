package com.cernecommerce.core.domain.exception.assistente;

/** {@code substituirDesde} não aponta para a última pergunta da conversa (409 SUBSTITUICAO_INVALIDA). */
public class SubstituicaoInvalidaException extends RuntimeException {
    public SubstituicaoInvalidaException() {
        super("Só dá para refazer a partir da última pergunta da conversa.");
    }
}
