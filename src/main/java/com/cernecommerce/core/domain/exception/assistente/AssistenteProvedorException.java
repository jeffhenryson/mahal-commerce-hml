package com.cernecommerce.core.domain.exception.assistente;

/** Falha do provedor de IA (rede, chave recusada, indisponível) — 502 ASSISTENTE_PROVEDOR. */
public class AssistenteProvedorException extends RuntimeException {

    private final boolean chaveRecusada;

    public AssistenteProvedorException(String message, boolean chaveRecusada, Throwable cause) {
        super(message, cause);
        this.chaveRecusada = chaveRecusada;
    }

    public boolean chaveRecusada() {
        return chaveRecusada;
    }
}
