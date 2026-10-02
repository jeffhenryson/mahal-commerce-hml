package com.cernecommerce.core.domain.model.pdv;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionProgressTest {

    private static final Instant T0 = Instant.parse("2026-09-28T22:00:00Z");
    private static final Instant T1 = T0.plusSeconds(600);

    @Test
    void transitions_onlyMoveForward_withTheShortcutFromPreparingToCollected() {
        assertThat(SessionStatus.NA_FILA.canTransitionTo(SessionStatus.PREPARANDO)).isTrue();
        assertThat(SessionStatus.NA_FILA.canTransitionTo(SessionStatus.ENTREGUE)).isFalse();
        assertThat(SessionStatus.NA_FILA.canTransitionTo(SessionStatus.RECOLHIDO)).isFalse();
        assertThat(SessionStatus.PREPARANDO.canTransitionTo(SessionStatus.ENTREGUE)).isTrue();
        assertThat(SessionStatus.PREPARANDO.canTransitionTo(SessionStatus.RECOLHIDO)).isTrue();
        assertThat(SessionStatus.ENTREGUE.canTransitionTo(SessionStatus.RECOLHIDO)).isTrue();
        assertThat(SessionStatus.ENTREGUE.canTransitionTo(SessionStatus.PREPARANDO)).isFalse();
        assertThat(SessionStatus.RECOLHIDO.canTransitionTo(SessionStatus.PREPARANDO)).isFalse();
        assertThat(SessionStatus.PREPARANDO.canTransitionTo(null)).isFalse();
    }

    @Test
    void advanceTo_stampsTheMatchingTime() {
        SessionProgress queued = SessionProgress.queued();
        SessionProgress preparing = queued.advanceTo(SessionStatus.PREPARANDO, T0);
        SessionProgress delivered = preparing.advanceTo(SessionStatus.ENTREGUE, T1);
        SessionProgress collected = delivered.advanceTo(SessionStatus.RECOLHIDO, T1.plusSeconds(60));

        assertThat(queued.startedAt()).isNull();
        assertThat(preparing.startedAt()).isEqualTo(T0);
        assertThat(delivered.deliveredAt()).isEqualTo(T1);
        assertThat(collected.startedAt()).isEqualTo(T0);
        assertThat(collected.collectedAt()).isEqualTo(T1.plusSeconds(60));
        assertThat(collected.isCollected()).isTrue();
    }

    @Test
    void advanceTo_invalidTransition_isRefused() {
        assertThatThrownBy(() -> SessionProgress.preparing(T0).advanceTo(SessionStatus.NA_FILA, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outsideTheQueue_startedAtIsRequired() {
        assertThatThrownBy(() -> new SessionProgress(SessionStatus.ENTREGUE, null, T1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionProgress_onlyOnMenuSessionLines() {
        assertThatThrownBy(() -> ComandaItem.of(1L, "AGUA", java.math.BigDecimal.ONE, java.math.BigDecimal.TEN,
                null, "Água", T0, com.cernecommerce.core.domain.model.pedido.ConsumptionMode.NORMAL, false, null,
                null, null, null, null, null, null, null, null, SessionProgress.preparing(T0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── PDV-F034 — sessão paga no final ─────────────────────────────────────────────────────

    @Test
    void payLater_startsPreparing_andKeepsTheMarkThroughEveryStep() {
        SessionProgress p = SessionProgress.preparingPayLater(T0);

        assertThat(p.status()).isEqualTo(SessionStatus.PREPARANDO);
        assertThat(p.startedAt()).isEqualTo(T0);
        assertThat(p.payLater()).isTrue();
        SessionProgress recolhida = p.advanceTo(SessionStatus.ENTREGUE, T1).advanceTo(SessionStatus.RECOLHIDO, T1);
        assertThat(recolhida.payLater()).isTrue();
        assertThat(recolhida.isCollected()).isTrue();
    }

    @Test
    void payLater_queuedRosh_carriesTheMarkIntoPreparation() {
        SessionProgress rosh = SessionProgress.queued(true).advanceTo(SessionStatus.PREPARANDO, T1);

        assertThat(rosh.payLater()).isTrue();
        assertThat(SessionProgress.queued().payLater()).isFalse();
    }

    @Test
    void payLater_neverAwaitsPayment() {
        assertThatThrownBy(() -> new SessionProgress(SessionStatus.AGUARDANDO_PAGAMENTO, null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
