package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OffSystemReceiptTest {

    @Test
    void register_normalizaNota_eAceitaProviderNulo() {
        OffSystemReceipt r = OffSystemReceipt.register(1L, new BigDecimal("35.00"), PaymentMethod.PIX, null,
                "  Pix na chave do dono ", "caixa1");

        assertThat(r.note()).isEqualTo("Pix na chave do dono");
        assertThat(r.provider()).isNull();
        assertThat(r.createdAt()).isNotNull();
    }

    @Test
    void register_notaEmBranco_viraNula() {
        OffSystemReceipt r = OffSystemReceipt.register(1L, BigDecimal.TEN, PaymentMethod.DEBITO,
                PaymentProvider.CIELO, "   ", "caixa1");

        assertThat(r.note()).isNull();
    }

    @Test
    void register_valorZeroOuNegativo_recusa() {
        assertThatThrownBy(() -> OffSystemReceipt.register(1L, BigDecimal.ZERO, PaymentMethod.PIX, null, null, "c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OffSystemReceipt.register(1L, new BigDecimal("-1"), PaymentMethod.PIX, null, null,
                "c")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"DINHEIRO", "GATEWAY_PIX", "MARCADO"})
    void register_formaForaDePixDebitoCredito_recusa(PaymentMethod method) {
        assertThatThrownBy(() -> OffSystemReceipt.register(1L, BigDecimal.TEN, method, null, null, "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_notaAcimaDe120_recusa() {
        assertThatThrownBy(() -> OffSystemReceipt.register(1L, BigDecimal.TEN, PaymentMethod.PIX, null,
                "x".repeat(121), "c")).isInstanceOf(IllegalArgumentException.class);
    }
}
