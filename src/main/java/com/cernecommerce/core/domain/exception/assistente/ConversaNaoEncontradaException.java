package com.cernecommerce.core.domain.exception.assistente;

/** Conversa ou ação inexistente, ou de outro usuário — os dois casos respondem 404. */
public class ConversaNaoEncontradaException extends RuntimeException {
    public ConversaNaoEncontradaException(String message) {
        super(message);
    }
}
