package com.cernecommerce.core.domain.model.pdv;

/**
 * Média de minutos acumulada valor a valor, ignorando nulos (PDV-C050). Dá o mesmo número que
 * {@code Math.round(stream.average())} sobre a lista inteira, sem precisar guardar a lista: os
 * indicadores de mesa somam o período em blocos e descartam cada bloco depois.
 */
public final class MinutesAverage {

    private long sum;
    private long count;

    public void add(Long minutes) {
        if (minutes != null) {
            sum += minutes;
            count++;
        }
    }

    /** Média arredondada; {@code null} sem nenhum valor. */
    public Long roundedOrNull() {
        return count == 0 ? null : Math.round((double) sum / count);
    }

    /** Média arredondada; zero sem nenhum valor. */
    public long roundedOrZero() {
        return count == 0 ? 0 : Math.round((double) sum / count);
    }
}
