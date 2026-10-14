package com.cernecommerce.core.domain.model.pagamento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPaymentTest {

    @Test
    void captured_startsAsCapturedWithTimestampsSet() {
        OrderPayment payment = OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("44.00"), null);

        assertThat(payment.id()).isNull();
        assertThat(payment.orderId()).isEqualTo(7L);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.capturedAt()).isNotNull();
        assertThat(payment.authorizedAt()).isNotNull();
        assertThat(payment.createdAt()).isNotNull();
        assertThat(payment.gatewayRef()).isNull();
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.DINHEIRO, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("-1"), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");
    }

    @Test
    void rejectsMissingOrderIdMethodOrStatus() {
        assertThatThrownBy(() -> OrderPayment.captured(null, PaymentMethod.DINHEIRO, BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("orderId");
        assertThatThrownBy(() -> OrderPayment.captured(7L, null, BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("method");
    }

    @Test
    void installments_onlyAllowedWithCredito() {
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.DINHEIRO, BigDecimal.TEN, 3))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CREDITO");

        OrderPayment credito = OrderPayment.captured(7L, PaymentMethod.CREDITO, BigDecimal.TEN, 3);
        assertThat(credito.installments()).isEqualTo(3);
    }

    @Test
    void installments_mustBeWithinRange() {
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.CREDITO, BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 e 24");
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.CREDITO, BigDecimal.TEN, 25))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 e 24");
    }

    @Test
    void statusAndCapturedAtHaveToCoexist() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> OrderPayment.of(1L, 7L, PaymentMethod.DINHEIRO, BigDecimal.TEN,
                PaymentStatus.CAPTURED, null, null, now, null, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CAPTURED");
        assertThatThrownBy(() -> OrderPayment.of(1L, 7L, PaymentMethod.DINHEIRO, BigDecimal.TEN,
                PaymentStatus.PENDING, null, null, now, now, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CAPTURED");
    }

    @Test
    void refunded_startsAsRefundedWithSameMethodAndAmount() {
        OrderPayment original = OrderPayment.captured(7L, PaymentMethod.CREDITO, new BigDecimal("44.00"), 3);

        OrderPayment refunded = OrderPayment.refunded(original);

        assertThat(refunded.id()).isNull();
        assertThat(refunded.orderId()).isEqualTo(7L);
        assertThat(refunded.method()).isEqualTo(PaymentMethod.CREDITO);
        assertThat(refunded.amount()).isEqualByComparingTo("44.00");
        assertThat(refunded.installments()).isEqualTo(3);
        assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.capturedAt()).isNull();
        assertThat(refunded.authorizedAt()).isNotNull();
        assertThat(refunded.gatewayRef()).isNull();
    }

    /** PED-F004 — estorno parcial: só a parte devolvida, com a sessão onde o dinheiro saiu. */
    @Test
    void refunded_parcial_levaOValorEASessaoDoEstorno() {
        OrderPayment original = OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("40.00"), null);

        OrderPayment refunded = OrderPayment.refunded(original, new BigDecimal("12.50"), 5L);

        assertThat(refunded.amount()).isEqualByComparingTo("12.50");
        assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.refundSessionId()).isEqualTo(5L);
    }

    @Test
    void refunded_parcial_recusaValorAcimaDoPagamento() {
        OrderPayment original = OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("40.00"), null);

        assertThatThrownBy(() -> OrderPayment.refunded(original, new BigDecimal("40.01"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundSessionId_soExisteEmLinhaRefunded() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> OrderPayment.of(1L, 7L, PaymentMethod.DINHEIRO, BigDecimal.TEN,
                PaymentStatus.CAPTURED, null, null, now, now, now, null, null, null, null, null, null, null, 5L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refunded_rejectsNonCapturedOriginal() {
        Instant now = Instant.now();
        OrderPayment pending = OrderPayment.of(1L, 7L, PaymentMethod.DINHEIRO, BigDecimal.TEN,
                PaymentStatus.PENDING, null, null, null, null, now);

        assertThatThrownBy(() -> OrderPayment.refunded(pending))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CAPTURED");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant createdAt = Instant.parse("2026-07-29T14:30:00Z");
        OrderPayment payment = OrderPayment.of(9L, 7L, PaymentMethod.PIX, new BigDecimal("22.00"),
                PaymentStatus.CAPTURED, null, null, createdAt, createdAt, createdAt);

        assertThat(payment.id()).isEqualTo(9L);
        assertThat(payment.method()).isEqualTo(PaymentMethod.PIX);
        assertThat(payment.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void pending_startsAsPendingWithNoGatewayRefOrTimestamps() {
        OrderPayment payment = OrderPayment.pending(7L, PaymentMethod.GATEWAY_PIX, new BigDecimal("44.00"));

        assertThat(payment.id()).isNull();
        assertThat(payment.orderId()).isEqualTo(7L);
        assertThat(payment.method()).isEqualTo(PaymentMethod.GATEWAY_PIX);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.gatewayRef()).isNull();
        assertThat(payment.authorizedAt()).isNull();
        assertThat(payment.capturedAt()).isNull();
        assertThat(payment.createdAt()).isNotNull();
    }

    @Test
    void confirmCaptured_transitionsPendingToCapturedPreservingIdentity() {
        OrderPayment pending = OrderPayment.of(5L, 7L, PaymentMethod.GATEWAY_PIX, new BigDecimal("44.00"),
                PaymentStatus.PENDING, null, null, null, null, Instant.parse("2026-08-03T10:00:00Z"));
        Instant capturedAt = Instant.parse("2026-08-03T10:05:00Z");

        OrderPayment captured = pending.confirmCaptured("txn-123", capturedAt);

        assertThat(captured.id()).isEqualTo(5L);
        assertThat(captured.orderId()).isEqualTo(7L);
        assertThat(captured.method()).isEqualTo(PaymentMethod.GATEWAY_PIX);
        assertThat(captured.amount()).isEqualByComparingTo("44.00");
        assertThat(captured.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(captured.gatewayRef()).isEqualTo("txn-123");
        assertThat(captured.authorizedAt()).isEqualTo(capturedAt);
        assertThat(captured.capturedAt()).isEqualTo(capturedAt);
        assertThat(captured.createdAt()).isEqualTo(Instant.parse("2026-08-03T10:00:00Z"));
    }

    @Test
    void confirmCaptured_rejectsNonPendingOrigin() {
        OrderPayment captured = OrderPayment.captured(7L, PaymentMethod.GATEWAY_PIX, BigDecimal.TEN, null);

        assertThatThrownBy(() -> captured.confirmCaptured("txn-123", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PENDING");
    }

    @Test
    void confirmCaptured_rejectsBlankGatewayRefOrNullCapturedAt() {
        OrderPayment pending = OrderPayment.pending(7L, PaymentMethod.GATEWAY_PIX, BigDecimal.TEN);

        assertThatThrownBy(() -> pending.confirmCaptured(" ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("gatewayRef");
        assertThatThrownBy(() -> pending.confirmCaptured("txn-123", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("capturedAt");
    }

    // ── PDV-F025 — canal e operadora ─────────────────────────────────────────────────────────

    @Test
    void captured_keepsChannelAndProvider_andTheRefundCopiesThem() {
        OrderPayment pago = OrderPayment.captured(1L, PaymentMethod.CREDITO, new BigDecimal("50.00"), 2,
                PaymentChannel.MAQUININHA, PaymentProvider.CIELO);

        OrderPayment estorno = OrderPayment.refunded(pago);

        assertThat(pago.channel()).isEqualTo(PaymentChannel.MAQUININHA);
        assertThat(pago.provider()).isEqualTo(PaymentProvider.CIELO);
        assertThat(estorno.channel()).isEqualTo(PaymentChannel.MAQUININHA);
        assertThat(estorno.provider()).isEqualTo(PaymentProvider.CIELO);
    }

    @Test
    void cash_hasNoChannel_andProviderNeedsAChannel() {
        assertThatThrownBy(() -> OrderPayment.captured(1L, PaymentMethod.DINHEIRO, BigDecimal.TEN, null,
                PaymentChannel.MAQUININHA, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderPayment.captured(1L, PaymentMethod.PIX, BigDecimal.TEN, null,
                null, PaymentProvider.INFINITYPAY)).isInstanceOf(IllegalArgumentException.class);
        assertThat(OrderPayment.captured(1L, PaymentMethod.PIX, BigDecimal.TEN, null, PaymentChannel.LINK, null)
                .channel()).isEqualTo(PaymentChannel.LINK);
    }
}
