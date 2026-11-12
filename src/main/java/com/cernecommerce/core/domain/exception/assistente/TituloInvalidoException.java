package com.cernecommerce.core.domain.exception.assistente;

/** Título de conversa vazio depois de normalizado (400 VALIDATION_ERROR). */
public class TituloInvalidoException extends RuntimeException {
    public TituloInvalidoException(String mensagem) {
        super(mensagem);
    }
}
