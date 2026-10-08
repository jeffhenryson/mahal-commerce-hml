package com.cernecommerce.core.domain.exception.crm;

/** Automação recusada na validação (destino sem o campo que exige, evento faltando, segredo ausente...). */
public class InvalidAutomationException extends RuntimeException {
    public InvalidAutomationException(String message) {
        super(message);
    }
}
