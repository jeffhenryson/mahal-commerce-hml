package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** PDV-F035 — a linha do tempo de uma sessão, calculada dos horários que a linha já grava. */
class SessionTimelineTest {

    private static final Instant LANCADA = Instant.parse("2026-10-02T22:00:00Z");

    private static Instant mais(long minutos) {
        return LANCADA.plusSeconds(minutos * 60);
    }

    private static ComandaItem linha(ConsumptionMode mode, Long linkedItemId, SessionProgress progress,
            Long closedIn) {
        return ComandaItem.of(1L, "SESS-2", BigDecimal.ONE, new BigDecimal("30.00"), null, "Sessão Premium",
                LANCADA, mode, false, linkedItemId, "Zomo Uva", null, closedIn, null, null, null, null, null,
                progress);
    }

    @Test
    void sessaoPagaAntes_esperaOPagamento_eMedeCadaFase() {
        SessionProgress p = new SessionProgress(SessionStatus.RECOLHIDO, mais(5), mais(15), mais(75));

        SessionTimeline t = SessionTimeline.of(linha(ConsumptionMode.SESSAO, null, p, 500L), mais(5));

        assertThat(t.esperouPor()).isEqualTo(SessionTimeline.Espera.PAGAMENTO);
        assertThat(t.pagaEm()).isEqualTo(mais(5));
        assertThat(t.esperaMin()).isEqualTo(5L);
        assertThat(t.preparoMin()).isEqualTo(10L);
        assertThat(t.naMesaMin()).isEqualTo(60L);
        assertThat(t.totalMin()).isEqualTo(75L);
        assertThat(t.desistida()).isFalse();
        assertThat(t.pagarNoFinal()).isFalse();
    }

    @Test
    void sessaoPagaNoFinal_naoEspera_eFicaSemPagamentoEnquantoAReceber() {
        SessionProgress p = SessionProgress.preparingPayLater(LANCADA)
                .advanceTo(SessionStatus.ENTREGUE, mais(8));

        SessionTimeline t = SessionTimeline.of(linha(ConsumptionMode.SESSAO, null, p, null), null);

        assertThat(t.pagarNoFinal()).isTrue();
        assertThat(t.esperouPor()).isNull();
        assertThat(t.esperaMin()).isZero();
        assertThat(t.preparoMin()).isEqualTo(8L);
        assertThat(t.pagaEm()).isNull();
        // Ainda na mesa: a fase não terminou.
        assertThat(t.naMesaMin()).isNull();
        assertThat(t.totalMin()).isNull();
    }

    @Test
    void sessaoDesistida_preparoVaiAteORecolhimento_esemTempoNaMesa() {
        SessionProgress p = new SessionProgress(SessionStatus.RECOLHIDO, mais(2), null, mais(6));

        SessionTimeline t = SessionTimeline.of(linha(ConsumptionMode.SESSAO, null, p, 500L), mais(2));

        assertThat(t.desistida()).isTrue();
        assertThat(t.preparoMin()).isEqualTo(4L);
        assertThat(t.naMesaMin()).isNull();
        assertThat(t.totalMin()).isEqualTo(6L);
    }

    /** Backfill da V132: início = lançamento, sem entrega. Não é desistência, é falta de registro. */
    @Test
    void sessaoDoBackfill_naoContaComoDesistida_eSoTemOTotal() {
        SessionProgress p = new SessionProgress(SessionStatus.RECOLHIDO, LANCADA, null, mais(90));

        SessionTimeline t = SessionTimeline.of(linha(ConsumptionMode.SESSAO, null, p, 500L), mais(90));

        assertThat(t.desistida()).isFalse();
        assertThat(t.esperaMin()).isNull();
        assertThat(t.preparoMin()).isNull();
        assertThat(t.naMesaMin()).isNull();
        assertThat(t.totalMin()).isEqualTo(90L);
    }

    @Test
    void roshDaFila_esperaOnarguileFicarLivre() {
        SessionProgress p = new SessionProgress(SessionStatus.PREPARANDO, mais(50), null, null);

        SessionTimeline t = SessionTimeline.of(linha(ConsumptionMode.ROSH_EXTRA, 9L, p, null), null);

        assertThat(t.esperouPor()).isEqualTo(SessionTimeline.Espera.FILA);
        assertThat(t.linkedItemId()).isEqualTo(9L);
        assertThat(t.esperaMin()).isEqualTo(50L);
        assertThat(t.preparoMin()).isNull();
    }

    @Test
    void aindaAguardandoPagamento_naoTemNenhumaFaseFechada() {
        SessionTimeline t = SessionTimeline.of(
                linha(ConsumptionMode.SESSAO, null, SessionProgress.awaitingPayment(), null), null);

        assertThat(t.status()).isEqualTo(SessionStatus.AGUARDANDO_PAGAMENTO);
        assertThat(t.esperaMin()).isNull();
        assertThat(t.preparoMin()).isNull();
        assertThat(t.totalMin()).isNull();
    }

    @Test
    void linhaDeCatalogo_naoTemLinhaDoTempo() {
        ComandaItem agua = ComandaItem.of(2L, "AGUA", BigDecimal.ONE, new BigDecimal("5.00"), null, "Água", LANCADA);

        assertThat(SessionTimeline.of(agua, null)).isNull();
    }
}
