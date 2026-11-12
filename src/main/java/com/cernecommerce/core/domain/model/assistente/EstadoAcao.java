package com.cernecommerce.core.domain.model.assistente;

/**
 * Ciclo da ação proposta pelo modelo. {@code EXECUTANDO} é só do servidor (trava o segundo
 * confirmar enquanto o primeiro roda); os demais são os que o front conhece.
 */
public enum EstadoAcao {
    PENDENTE, EXECUTANDO, EXECUTADA, RECUSADA, EXPIRADA, FALHOU;

    public boolean finalizado() {
        return this == EXECUTADA || this == RECUSADA || this == EXPIRADA || this == FALHOU;
    }
}
