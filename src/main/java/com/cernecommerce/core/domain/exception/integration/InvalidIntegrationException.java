package com.cernecommerce.core.domain.exception.integration;

/** Configuração de integração (WhatsApp, plataforma de automação) recusada na validação. */
public class InvalidIntegrationException extends RuntimeException {
    public InvalidIntegrationException(String message) {
        super(message);
    }
}
