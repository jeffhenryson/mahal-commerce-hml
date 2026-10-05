package com.cernecommerce.core.domain.exception.email;

/** Configuração da integração de e-mail incompleta ou inválida — a mensagem diz o que falta. */
public class InvalidEmailIntegrationException extends RuntimeException {

    public InvalidEmailIntegrationException(String message) {
        super(message);
    }
}
