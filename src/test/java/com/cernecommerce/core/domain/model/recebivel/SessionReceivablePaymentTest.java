package com.cernecommerce.core.domain.model.recebivel;

import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Extrato de quitações de marcado do caixa: troco na última linha em dinheiro, estorno como REFUNDED. */
class SessionReceivablePaymentTest {

    private static final Instant T0 = Instant.parse("2026-11-13T15:00:00Z");

    private static ReceivablePayment payment(long id, long receivableId, long batchId, String amount,
            PaymentMethod method, PaymentProvider provider, Instant at) {
        return new ReceivablePayment(id, receivableId, batchId, 7L, new BigDecimal(amount), method, null,
                provider == null ? null : PaymentChannel.MAQUININHA, provider, 62L, "bia", at);
    }

    private static ReceivablePaymentBatch batch(long id, String change) {
        return new ReceivablePaymentBatch(id, 7L, 62L, new BigDecimal(change), "bia", T0);
    }

    @Test
    void troco_vaiNaUltimaLinhaEmDinheiroDoLote_eAmountEhOEntregue() {
        // Lote 1: dois marcados (30 + 20), pago com 10 de Pix e 50 em dinheiro → troco 10.
        // O dinheiro abateu 20 do primeiro e 20 do segundo; o troco fica na linha 3.
        List<ReceivablePayment> payments = List.of(
                payment(1, 100, 1, "10.00", PaymentMethod.PIX, PaymentProvider.INFINITYPAY, T0),
                payment(2, 100, 1, "20.00", PaymentMethod.DINHEIRO, null, T0),
                payment(3, 101, 1, "20.00", PaymentMethod.DINHEIRO, null, T0));

        List<SessionReceivablePayment> result = SessionReceivablePayment.assemble(payments,
                List.of(batch(1, "10.00")), Set.of(), Map.of(7L, "Ana"));

        assertThat(result).extracting(SessionReceivablePayment::id, SessionReceivablePayment::amount,
                        SessionReceivablePayment::changeAmount)
                .containsExactly(
                        tuple(3L, new BigDecimal("30.00"), new BigDecimal("10.00")),
                        tuple(2L, new BigDecimal("20.00"), BigDecimal.ZERO),
                        tuple(1L, new BigDecimal("10.00"), BigDecimal.ZERO));
        BigDecimal applied = result.stream().map(p -> p.amount().subtract(p.changeAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(applied).isEqualByComparingTo("50.00");
        assertThat(result).allSatisfy(p -> {
            assertThat(p.customerName()).isEqualTo("Ana");
            assertThat(p.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(p.username()).isEqualTo("bia");
        });
        assertThat(result.get(2).provider()).isEqualTo(PaymentProvider.INFINITYPAY);
    }

    @Test
    void loteEstornado_saiComoRefunded() {
        List<ReceivablePayment> payments = List.of(
                payment(1, 100, 1, "15.00", PaymentMethod.CREDITO, PaymentProvider.CIELO, T0),
                payment(2, 101, 2, "25.00", PaymentMethod.PIX, null, T0.plusSeconds(60)));

        List<SessionReceivablePayment> result = SessionReceivablePayment.assemble(payments,
                List.of(batch(1, "0.00"), batch(2, "0.00")), Set.of(1L), Map.of(7L, "Ana"));

        assertThat(result).extracting(SessionReceivablePayment::id, SessionReceivablePayment::status)
                .containsExactly(tuple(2L, PaymentStatus.CAPTURED), tuple(1L, PaymentStatus.REFUNDED));
    }

    @Test
    void loteComTrocoSemLinhaEmDinheiro_naoInventaTroco() {
        // Saldo 50 pago com 50 de Pix e 20 em dinheiro: o dinheiro todo volta como troco, sem linha.
        List<ReceivablePayment> payments = List.of(
                payment(1, 100, 1, "50.00", PaymentMethod.PIX, null, T0));

        List<SessionReceivablePayment> result = SessionReceivablePayment.assemble(payments,
                List.of(batch(1, "20.00")), Set.of(), Map.of());

        assertThat(result).singleElement().satisfies(p -> {
            assertThat(p.amount()).isEqualByComparingTo("50.00");
            assertThat(p.changeAmount()).isEqualByComparingTo("0");
            assertThat(p.customerName()).isNull();
        });
    }
}
