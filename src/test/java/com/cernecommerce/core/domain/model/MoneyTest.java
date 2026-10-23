package com.cernecommerce.core.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Money#of} — a normalização de valor monetário vindo de fora. O caso que a motivou: o
 * cliente somou preço × quantidade em {@code double} e mandou {@code 76.28999999999999}.
 */
class MoneyTest {

    @Test
    void of_roundsFloatingPointNoiseToTwoPlaces() {
        assertThat(Money.of(new BigDecimal("76.28999999999999"))).isEqualTo(new BigDecimal("76.29"));
        assertThat(Money.of(new BigDecimal("76.29000000000001"))).isEqualTo(new BigDecimal("76.29"));
    }

    /** HALF_UP, a mesma regra de todo cálculo do domínio — meio centavo sobe. */
    @Test
    void of_roundsHalfUp() {
        assertThat(Money.of(new BigDecimal("1.005"))).isEqualTo(new BigDecimal("1.01"));
        assertThat(Money.of(new BigDecimal("1.004"))).isEqualTo(new BigDecimal("1.00"));
    }

    /** {@code isEqualTo} e não {@code isEqualByComparingTo}: a escala também é o contrato. */
    @Test
    void of_alwaysReturnsScaleTwo() {
        assertThat(Money.of(new BigDecimal("50"))).isEqualTo(new BigDecimal("50.00"));
        assertThat(Money.of(new BigDecimal("50.5"))).isEqualTo(new BigDecimal("50.50"));
    }

    @Test
    void of_null_isNull() {
        assertThat(Money.of(null)).isNull();
    }
}
