package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InvalidPromotionPeriodException;
import com.cernecommerce.core.domain.exception.estoque.InvalidPromotionPriceException;
import com.cernecommerce.core.domain.exception.estoque.InvalidPromotionQuantityException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityPromotionTest {

    private static final Instant AGORA = Instant.parse("2026-11-01T12:00:00Z");

    private static QuantityPromotion promo(boolean active, Instant startsAt, Instant endsAt) {
        return new QuantityPromotion(1L, "Seda", null, 4, new BigDecimal("10"), List.of("SEDA-A"),
                Set.of(PromotionChannel.PDV), active, startsAt, endsAt, null, null, null, null);
    }

    @Test
    void statusPorFlagEPeriodo() {
        assertThat(promo(false, null, null).status(AGORA)).isEqualTo(PromotionStatus.INATIVA);
        assertThat(promo(true, Instant.parse("2026-11-02T00:00:00Z"), null).status(AGORA))
                .isEqualTo(PromotionStatus.AGENDADA);
        assertThat(promo(true, null, Instant.parse("2026-10-31T00:00:00Z")).status(AGORA))
                .isEqualTo(PromotionStatus.ENCERRADA);
        assertThat(promo(true, null, AGORA).status(AGORA)).isEqualTo(PromotionStatus.ENCERRADA);
        assertThat(promo(true, AGORA, null).status(AGORA)).isEqualTo(PromotionStatus.ATIVA);
        assertThat(promo(true, null, null).status(AGORA)).isEqualTo(PromotionStatus.ATIVA);
    }

    @Test
    void vigenteSoNoCanalDaLista() {
        assertThat(promo(true, null, null).isVigente(AGORA, PromotionChannel.PDV)).isTrue();
        assertThat(promo(true, null, null).isVigente(AGORA, PromotionChannel.MARKETPLACE)).isFalse();
        assertThat(promo(false, null, null).isVigente(AGORA, PromotionChannel.PDV)).isFalse();
    }

    @Test
    void validacoes() {
        assertThatThrownBy(() -> new QuantityPromotion(null, "x", null, 1, BigDecimal.TEN, List.of("A"),
                Set.of(PromotionChannel.PDV), true, null, null, null, null, null, null))
                .isInstanceOf(InvalidPromotionQuantityException.class);
        assertThatThrownBy(() -> new QuantityPromotion(null, "x", null, 2, BigDecimal.ZERO, List.of("A"),
                Set.of(PromotionChannel.PDV), true, null, null, null, null, null, null))
                .isInstanceOf(InvalidPromotionPriceException.class);
        assertThatThrownBy(() -> promo(true, AGORA, AGORA)).isInstanceOf(InvalidPromotionPeriodException.class);
    }

    @Test
    void label() {
        assertThat(promo(true, null, null).label()).isEqualTo("4 por R$ 10");
        assertThat(new QuantityPromotion(1L, "x", null, 3, new BigDecimal("9.99"), List.of("A"),
                Set.of(PromotionChannel.PDV), true, null, null, null, null, null, null).label())
                .isEqualTo("3 por R$ 9,99");
    }
}
