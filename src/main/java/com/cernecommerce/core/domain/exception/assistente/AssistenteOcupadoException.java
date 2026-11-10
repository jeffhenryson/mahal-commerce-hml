package com.cernecommerce.core.domain.exception.assistente;

/**
 * Já há uma resposta em andamento nesta conversa, ou o executor do assistente está cheio
 * (409 ASSISTENTE_OCUPADO). Recusado antes de abrir o stream.
 */
public class AssistenteOcupadoException extends RuntimeException {
    public AssistenteOcupadoException(String message) {
        super(message);
    }
}
