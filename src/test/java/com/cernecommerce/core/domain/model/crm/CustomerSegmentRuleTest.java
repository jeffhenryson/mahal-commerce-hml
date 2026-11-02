package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerSegmentRuleTest {

    private static final Instant AGORA = Instant.parse("2026-11-02T12:00:00Z");

    private static Instant diasAtras(int dias) {
        return AGORA.minus(Duration.ofDays(dias));
    }

    @Test
    void vipVenceTudo() {
        assertThat(CustomerSegmentRule.segmento(true, 0, null, AGORA)).isEqualTo("VIP");
        assertThat(CustomerSegmentRule.segmento(true, 10, diasAtras(200), AGORA)).isEqualTo("VIP");
    }

    @Test
    void semCompraENovo() {
        assertThat(CustomerSegmentRule.segmento(false, 0, null, AGORA)).isEqualTo("Novo");
    }

    @Test
    void recenciaDefineInativoEEmRisco() {
        assertThat(CustomerSegmentRule.segmento(false, 5, diasAtras(91), AGORA)).isEqualTo("Inativo");
        assertThat(CustomerSegmentRule.segmento(false, 5, diasAtras(90), AGORA)).isEqualTo("Em Risco");
        assertThat(CustomerSegmentRule.segmento(false, 5, diasAtras(46), AGORA)).isEqualTo("Em Risco");
        assertThat(CustomerSegmentRule.segmento(false, 5, diasAtras(45), AGORA)).isEqualTo("Recorrente");
    }

    @Test
    void frequenciaDefineRecorrente() {
        assertThat(CustomerSegmentRule.segmento(false, 3, diasAtras(2), AGORA)).isEqualTo("Recorrente");
        assertThat(CustomerSegmentRule.segmento(false, 2, diasAtras(2), AGORA)).isEqualTo("Novo");
    }
}
