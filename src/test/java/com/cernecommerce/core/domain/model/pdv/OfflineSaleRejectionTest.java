package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.exception.pdv.OfflineRejectionAlreadyResolvedException;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PDV-F043 — a venda offline recusada e a revisão dela, sem banco. */
class OfflineSaleRejectionTest {

    private static final Instant T0 = Instant.parse("2026-10-09T15:00:00Z");

    private static OfflineSaleRejection recusada() {
        return OfflineSaleRejection.create(7L, "c0ffee00-0000-4000-8000-000000000001", T0, null,
                List.of(new OfflineSaleItem("LM-AZUL-MACO", BigDecimal.ONE, null, null)),
                List.of(new OfflineSalePayment(PaymentMethod.DINHEIRO, new BigDecimal("12.00"), null)),
                "INSUFFICIENT_STOCK", "sem saldo", "caixa1", T0).withId(1L);
    }

    @Test
    void nasce_pendente_comAVendaComoChegou() {
        OfflineSaleRejection r = recusada();

        assertThat(r.isPending()).isTrue();
        assertThat(r.items()).singleElement().satisfies(i -> assertThat(i.sku()).isEqualTo("LM-AZUL-MACO"));
        assertThat(r.errorCode()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void retried_resolveComOPedido() {
        OfflineSaleRejection r = recusada().retried(99L, "gerente", T0.plusSeconds(60));

        assertThat(r.isPending()).isFalse();
        assertThat(r.resolution()).isEqualTo(OfflineRejectionResolution.RETRIED);
        assertThat(r.orderId()).isEqualTo(99L);
        assertThat(r.resolvedBy()).isEqualTo("gerente");
    }

    /** Reenviada e recusada de novo: continua na revisão, com o motivo atualizado. */
    @Test
    void stillFailing_continuaPendenteComOMotivoNovo() {
        OfflineSaleRejection r = recusada().stillFailing("PRODUCT_NOT_FOUND", "sumiu");

        assertThat(r.isPending()).isTrue();
        assertThat(r.errorCode()).isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void discarded_exigeMotivo() {
        assertThatThrownBy(() -> recusada().discarded("  ", "gerente", T0))
                .isInstanceOf(IllegalArgumentException.class);

        OfflineSaleRejection r = recusada().discarded(" Cliente devolveu o maço ", "gerente", T0);
        assertThat(r.resolution()).isEqualTo(OfflineRejectionResolution.DISCARDED);
        assertThat(r.resolutionNote()).isEqualTo("Cliente devolveu o maço");
    }

    /** Resolver duas vezes seria registrar a mesma venda em dobro, ou descartar uma que já virou pedido. */
    @Test
    void resolverDuasVezes_eRecusado() {
        OfflineSaleRejection resolvida = recusada().retried(99L, "gerente", T0);

        assertThatThrownBy(() -> resolvida.discarded("motivo", "gerente", T0))
                .isInstanceOf(OfflineRejectionAlreadyResolvedException.class);
        assertThatThrownBy(() -> resolvida.retried(100L, "gerente", T0))
                .isInstanceOf(OfflineRejectionAlreadyResolvedException.class);
    }

    @Test
    void pagamento_soDinheiroDebitoECreditoOffline() {
        assertThat(new OfflineSalePayment(PaymentMethod.DEBITO, BigDecimal.TEN, null).allowedOffline()).isTrue();
        assertThat(new OfflineSalePayment(PaymentMethod.PIX, BigDecimal.TEN, null).allowedOffline()).isFalse();
        assertThat(new OfflineSalePayment(PaymentMethod.MARCADO, BigDecimal.TEN, null).allowedOffline()).isFalse();
    }
}
