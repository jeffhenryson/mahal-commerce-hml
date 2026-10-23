package com.cernecommerce.core.domain.model.recebivel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PED-F004 — estorno parcial de pedido marcado abate o recebível. */
class CustomerReceivableTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final LocalDate DUE = LocalDate.parse("2026-11-12");

    private static CustomerReceivable open(String amount) {
        return CustomerReceivable.open(1L, 2L, null, 3L, new BigDecimal(amount), DUE, "caixa", NOW, List.of());
    }

    @Test
    void reducedBy_parteDoSaldo_continuaEmAberto() {
        CustomerReceivable reduced = open("50.00").reducedBy(new BigDecimal("20.00"), "estorno", "gerente", NOW);

        assertThat(reduced.amount()).isEqualByComparingTo("30.00");
        assertThat(reduced.amountOpen()).isEqualByComparingTo("30.00");
        assertThat(reduced.status()).isEqualTo(ReceivableStatus.ABERTO);
    }

    @Test
    void reducedBy_todoOSaldoSemPagamento_cancela() {
        CustomerReceivable reduced = open("50.00").reducedBy(new BigDecimal("50.00"), "estorno", "gerente", NOW);

        assertThat(reduced.status()).isEqualTo(ReceivableStatus.CANCELADO);
        assertThat(reduced.cancelReason()).isEqualTo("estorno");
    }

    @Test
    void reducedBy_oQueRestaJaFoiPago_quita() {
        CustomerReceivable partlyPaid = open("50.00").applyPayment(new BigDecimal("20.00"), NOW, DUE.minusDays(1));

        CustomerReceivable reduced = partlyPaid.reducedBy(new BigDecimal("30.00"), "estorno", "gerente", NOW);

        assertThat(reduced.amount()).isEqualByComparingTo("20.00");
        assertThat(reduced.status()).isEqualTo(ReceivableStatus.QUITADO);
        assertThat(reduced.settledAt()).isEqualTo(NOW);
    }

    @Test
    void reducedBy_acimaDoSaldo_recusa() {
        assertThatThrownBy(() -> open("50.00").reducedBy(new BigDecimal("50.01"), "estorno", "gerente", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── V173: estorno de quitação ────────────────────────────────────────────────────────────

    @Test
    void revertPayment_quitadoInteiro_voltaAAberto() {
        CustomerReceivable settled = open("50.00").applyPayment(new BigDecimal("50.00"), NOW, DUE.minusDays(1));

        CustomerReceivable reverted = settled.revertPayment(new BigDecimal("50.00"), DUE.minusDays(1));

        assertThat(reverted.status()).isEqualTo(ReceivableStatus.ABERTO);
        assertThat(reverted.amountOpen()).isEqualByComparingTo("50.00");
        assertThat(reverted.settledAt()).isNull();
    }

    @Test
    void revertPayment_sobraPago_ficaParcial() {
        CustomerReceivable settled = open("50.00").applyPayment(new BigDecimal("50.00"), NOW, DUE.minusDays(1));

        CustomerReceivable reverted = settled.revertPayment(new BigDecimal("30.00"), DUE.minusDays(1));

        assertThat(reverted.status()).isEqualTo(ReceivableStatus.PARCIAL);
        assertThat(reverted.amountOpen()).isEqualByComparingTo("30.00");
    }

    @Test
    void revertPayment_prazoJaPassou_voltaVencido() {
        CustomerReceivable settled = open("50.00").applyPayment(new BigDecimal("50.00"), NOW, DUE.minusDays(1));

        assertThat(settled.revertPayment(new BigDecimal("50.00"), DUE.plusDays(1)).status())
                .isEqualTo(ReceivableStatus.VENCIDO);
    }

    @Test
    void revertPayment_acimaDoPago_recusa() {
        CustomerReceivable partlyPaid = open("50.00").applyPayment(new BigDecimal("20.00"), NOW, DUE.minusDays(1));

        assertThatThrownBy(() -> partlyPaid.revertPayment(new BigDecimal("20.01"), DUE.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revertPayment_cancelado_recusa() {
        CustomerReceivable cancelled = open("50.00").cancelled("perdão", "gerente", NOW);

        assertThatThrownBy(() -> cancelled.revertPayment(new BigDecimal("1.00"), DUE))
                .isInstanceOf(IllegalStateException.class);
    }
}
