package com.cernecommerce.core.domain.exception.assistente;

/** Mensagem inexistente, do usuário (não avaliável) ou de conversa de outro usuário (404). */
public class MensagemNaoEncontradaException extends RuntimeException {
    public MensagemNaoEncontradaException() {
        super("Mensagem não encontrada");
    }
}
