package com.cernecommerce.core.domain.model.salao;

/**
 * Por que a essência está (ou não) no QR. O QR oferece ABERTA e FECHADA — a fechada abre na 1ª sessão
 * ({@code consumeSession}); SEM_FAIXA o lançamento recusaria, então só o admin vê.
 */
public enum Disponibilidade {
    /** Lata aberta no depósito das sessões. */
    ABERTA,
    /** Só lata fechada no depósito das sessões. */
    FECHADA,
    /** Com estoque, mas sem faixa de sessão ativa. */
    SEM_FAIXA;

    public boolean noQr() {
        return this != SEM_FAIXA;
    }
}
