package com.cernecommerce.core.domain.exception.assistente;

/** Refazer um trecho cuja ação já foi executada (409 ACAO_JA_EXECUTADA): refazer não desfaz uma escrita. */
public class AcaoJaExecutadaException extends RuntimeException {
    public AcaoJaExecutadaException() {
        super("Esta resposta já executou uma alteração. Faça uma nova pergunta em vez de refazer.");
    }
}
