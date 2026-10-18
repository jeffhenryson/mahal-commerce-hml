package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;

/**
 * Uma fatia dos indicadores de mesa — um atendente, uma mesa ou uma hora (PDV-C050): quantas mesas,
 * quanto receberam e a permanência média. Acumulada mesa a mesa, para o período não ficar inteiro na
 * memória.
 */
public final class AnalyticsGroup {

    private int mesas;
    private BigDecimal receita = BigDecimal.ZERO;
    private final MinutesAverage permanencia = new MinutesAverage();

    public void add(BigDecimal totalPaid, Long durationMinutes) {
        mesas++;
        receita = receita.add(totalPaid);
        permanencia.add(durationMinutes);
    }

    public int mesas() {
        return mesas;
    }

    public BigDecimal receita() {
        return receita;
    }

    public long permanenciaMediaMin() {
        return permanencia.roundedOrZero();
    }
}
