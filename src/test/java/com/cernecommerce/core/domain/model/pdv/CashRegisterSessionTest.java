package com.cernecommerce.core.domain.model.pdv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashRegisterSessionTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");

    private static CashRegisterSession open() {
        return CashRegisterSession.open("caixa1", new BigDecimal("200.00"), "LOJA-01");
    }

    // ── Abertura ─────────────────────────────────────────────────────────────────────────────

    @Test
    void open_startsWithoutAnyClosingData() {
        CashRegisterSession session = open();

        assertThat(session.id()).isNull();
        assertThat(session.operator()).isEqualTo("caixa1");
        assertThat(session.openingAmount()).isEqualByComparingTo("200.00");
        assertThat(session.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(session.status()).isEqualTo(CashRegisterSession.Status.OPEN);
        assertThat(session.isOpen()).isTrue();
        assertThat(session.closedAt()).isNull();
        assertThat(session.countedAmount()).isNull();
        assertThat(session.diverges()).isFalse();
    }

    @Test
    void open_acceptsZeroOpeningAmount() {
        // Caixa sem fundo de troco é operacionalmente ruim, mas não é inválido.
        assertThat(CashRegisterSession.open("caixa1", BigDecimal.ZERO, "LOJA-01").openingAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    void open_rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> CashRegisterSession.open("  ", BigDecimal.TEN, "LOJA-01"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("operator");
        assertThatThrownBy(() -> CashRegisterSession.open("caixa1", new BigDecimal("-1"), "LOJA-01"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("openingAmount");
        assertThatThrownBy(() -> CashRegisterSession.open("caixa1", BigDecimal.TEN, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("warehouseCode");
    }

    // ── Fechamento ───────────────────────────────────────────────────────────────────────────

    @Test
    void closedWith_stampsExpectedCountedAndDifference() {
        CashRegisterSession closed = open()
                .closedWith(new BigDecimal("500.00"), new BigDecimal("495.00"), "gerente");

        assertThat(closed.status()).isEqualTo(CashRegisterSession.Status.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.closedBy()).isEqualTo("gerente");
        assertThat(closed.expectedAmount()).isEqualByComparingTo("500.00");
        assertThat(closed.countedAmount()).isEqualByComparingTo("495.00");
        assertThat(closed.differenceAmount()).isEqualByComparingTo("-5.00");
    }

    @Test
    void closedWith_doesNotBlockOnDivergence() {
        // Espelha o balanço de inventário: a divergência é o achado do fechamento, não um erro.
        CashRegisterSession closed = open()
                .closedWith(new BigDecimal("500.00"), new BigDecimal("380.00"), "gerente");

        assertThat(closed.status()).isEqualTo(CashRegisterSession.Status.CLOSED);
        assertThat(closed.diverges()).isTrue();
    }

    @Test
    void closedWith_allowsSurplusAsWellAsShortage() {
        // Sobra no caixa é tão informativa quanto falta — e igualmente válida.
        assertThat(open().closedWith(new BigDecimal("500.00"), new BigDecimal("512.00"), "gerente")
                .differenceAmount()).isEqualByComparingTo("12.00");
    }

    @Test
    void diverges_isFalseWhenCountedMatchesExpected() {
        CashRegisterSession closed = open()
                .closedWith(new BigDecimal("500.00"), new BigDecimal("500.00"), "gerente");

        assertThat(closed.diverges()).isFalse();
        assertThat(closed.differenceAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void closedWith_refusesToCloseTwice() {
        // Refechar reescreveria a conferência original, que é o registro que precisa ser imutável.
        CashRegisterSession closed = open()
                .closedWith(new BigDecimal("500.00"), new BigDecimal("500.00"), "gerente");

        assertThatThrownBy(() -> closed.closedWith(new BigDecimal("500.00"), new BigDecimal("400.00"), "outro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já está fechada");
    }

    @Test
    void closedWith_requiresExpectedCountedAndCloser() {
        CashRegisterSession session = open();

        assertThatThrownBy(() -> session.closedWith(null, BigDecimal.TEN, "gerente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedAmount");
        assertThatThrownBy(() -> session.closedWith(BigDecimal.TEN, null, "gerente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("countedAmount");
        assertThatThrownBy(() -> session.closedWith(BigDecimal.TEN, BigDecimal.TEN, " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("closedBy");
    }

    // ── Invariantes de reconstituição ────────────────────────────────────────────────────────

    @Test
    void of_rejectsClosedStatusWithoutTimestamp() {
        assertThatThrownBy(() -> CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.TEN, "LOJA-01",
                null, "gerente", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                CashRegisterSession.Status.CLOSED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("closedAt");
    }

    @Test
    void of_rejectsOpenStatusWithClosingTimestamp() {
        assertThatThrownBy(() -> CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.TEN, "LOJA-01",
                NOW, null, null, null, null, CashRegisterSession.Status.OPEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("closedAt");
    }

    @Test
    void of_rejectsClosedSessionWithoutCountedAmount() {
        assertThatThrownBy(() -> CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.TEN, "LOJA-01",
                NOW, "gerente", BigDecimal.TEN, null, null, CashRegisterSession.Status.CLOSED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("countedAmount");
    }

    @Test
    void of_rejectsDifferenceThatDoesNotMatchTheOtherTwo() {
        assertThatThrownBy(() -> CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.TEN, "LOJA-01",
                NOW, "gerente", new BigDecimal("500.00"), new BigDecimal("495.00"),
                new BigDecimal("99.00"), CashRegisterSession.Status.CLOSED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("differenceAmount");
    }

    @Test
    void of_acceptsNegativeDifference() {
        CashRegisterSession closed = CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.TEN, "LOJA-01",
                NOW, "gerente", new BigDecimal("500.00"), new BigDecimal("495.00"),
                new BigDecimal("-5.00"), CashRegisterSession.Status.CLOSED);

        assertThat(closed.differenceAmount()).isEqualByComparingTo("-5.00");
        assertThat(closed.diverges()).isTrue();
    }

    // ── Propriedade ──────────────────────────────────────────────────────────────────────────

    @Test
    void belongsTo_identifiesTheOwningOperator() {
        CashRegisterSession session = open();

        assertThat(session.belongsTo("caixa1")).isTrue();
        assertThat(session.belongsTo("caixa2")).isFalse();
        assertThat(session.belongsTo(null)).isFalse();
    }

    @Test
    void closedWith_recordsNormalizedClosingNotes() {
        CashRegisterSession closed = open()
                .closedWith(BigDecimal.TEN, BigDecimal.TEN, "admin", "  saiu sem fechar ");

        assertThat(closed.closingNotes()).isEqualTo("saiu sem fechar");
    }

    @Test
    void closedWith_blankNotesBecomeNull() {
        assertThat(open().closedWith(BigDecimal.TEN, BigDecimal.TEN, "admin", "   ").closingNotes()).isNull();
    }

    @Test
    void closingNotes_rejectsTooLongText() {
        String longo = "x".repeat(CashRegisterSession.MAX_CLOSING_NOTES + 1);
        assertThatThrownBy(() -> open().closedWith(BigDecimal.TEN, BigDecimal.TEN, "admin", longo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closingNotes_cannotExistOnOpenSession() {
        assertThatThrownBy(() -> CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN, "nota"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── PDV-F037: sugestão de fechar depois de 12h ───────────────────────────────────────────

    @Test
    void closeSuggested_onlyAfterTwelveHoursOpen() {
        CashRegisterSession caixa = CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);

        assertThat(caixa.closeSuggested(NOW.plus(Duration.ofHours(11).plusMinutes(59)))).isFalse();
        assertThat(caixa.closeSuggested(NOW.plus(Duration.ofHours(12)))).isTrue();
        assertThat(caixa.openHours(NOW.plus(Duration.ofHours(13).plusMinutes(30)))).isEqualTo(13);
    }

    @Test
    void closeSuggested_neverOnClosedSession() {
        CashRegisterSession fechado = CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.TEN, "LOJA-01",
                NOW.plus(Duration.ofHours(14)), "caixa1", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                CashRegisterSession.Status.CLOSED);

        assertThat(fechado.closeSuggested(NOW.plus(Duration.ofHours(20)))).isFalse();
        assertThat(fechado.openHours(NOW.plus(Duration.ofHours(20)))).isEqualTo(14);
    }
}
