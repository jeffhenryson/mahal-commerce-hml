package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Desconto do chefe: percentual inteiro sobre a base elegível, conferido contra o valor rateado. */
class BossDiscountTest {

    @Test
    void expected_isPercentOfTheEligibleBase_roundedDown() {
        assertThat(BossDiscount.expected(5, new BigDecimal("100.00"))).isEqualByComparingTo("5.00");
        assertThat(BossDiscount.expected(3, new BigDecimal("33.33"))).isEqualByComparingTo("0.99");
        assertThat(BossDiscount.expected(5, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void requireMatches_acceptsTheExactValue() {
        assertThatCode(() -> BossDiscount.requireMatches(5, new BigDecimal("5.00"), new BigDecimal("100.00"), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void requireMatches_toleratesOneCentPerEligibleLine() {
        // 3 linhas: até R$ 0,03 de diferença de arredondamento do rateio.
        assertThatCode(() -> BossDiscount.requireMatches(5, new BigDecimal("5.03"), new BigDecimal("100.00"), 3))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BossDiscount.requireMatches(5, new BigDecimal("5.04"), new BigDecimal("100.00"), 3))
                .isInstanceOf(DiscountLimitExceededException.class);
    }

    @Test
    void requireMatches_refusesAValueThatDoesNotMatchThePercent() {
        assertThatThrownBy(() -> BossDiscount.requireMatches(5, new BigDecimal("7.50"), new BigDecimal("100.00"), 1))
                .isInstanceOf(DiscountLimitExceededException.class)
                .hasMessageContaining("5%");
    }

    @Test
    void requireMatches_percentWithoutValueIsRefused() {
        assertThatThrownBy(() -> BossDiscount.requireMatches(3, BigDecimal.ZERO, new BigDecimal("100.00"), 1))
                .isInstanceOf(DiscountLimitExceededException.class);
    }

    @Test
    void eligibleLines_leavesOutTheLinesOutsideTheBenefits() {
        OrderItem comum = OrderItem.of(1L, "CARV", BigDecimal.ONE, new BigDecimal("100.00"), null, null, null);
        OrderItem cigarro = OrderItem.of(2L, "CIG", BigDecimal.ONE, new BigDecimal("50.00"), null, null, null)
                .withBenefitsExcluded(true);

        assertThat(BossDiscount.eligibleLines(List.of(comum, cigarro))).containsExactly(comum);
    }
}
