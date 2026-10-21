package com.cernecommerce.core.domain.exception.salao;

/**
 * Quem tentou entrar na mesa pelo CPF/celular errou a confirmação: os 4 últimos dígitos do celular
 * (entrou pelo CPF) ou os 3 primeiros do CPF (entrou pelo celular).
 */
public class ConfirmacaoInvalidaException extends RuntimeException {
    public ConfirmacaoInvalidaException(String message) {
        super(message);
    }
}
