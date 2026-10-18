package com.cernecommerce.core.domain.model.pdv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/** PDV-C050 — a média acumulada tem que dar o mesmo número da média sobre a lista inteira. */
class MinutesAverageTest {

    @Test
    void ignoresNullsAndRoundsLikeTheStreamAverage() {
        MinutesAverage avg = new MinutesAverage();
        avg.add(10L);
        avg.add(null);
        avg.add(11L);

        // (10 + 11) / 2 = 10,5 → 11, como Math.round(LongStream.of(10, 11).average())
        assertThat(avg.roundedOrNull()).isEqualTo(Math.round(LongStream.of(10, 11).average().orElseThrow()));
        assertThat(avg.roundedOrZero()).isEqualTo(11L);
    }

    @Test
    void withoutValues_nullOrZeroDependingOnTheIndicator() {
        MinutesAverage avg = new MinutesAverage();
        avg.add(null);

        assertThat(avg.roundedOrNull()).isNull();
        assertThat(avg.roundedOrZero()).isZero();
    }

    @Test
    void analyticsGroup_countsSumsAndAverages() {
        AnalyticsGroup group = new AnalyticsGroup();
        group.add(new BigDecimal("30.00"), 60L);
        group.add(new BigDecimal("12.50"), null);

        assertThat(group.mesas()).isEqualTo(2);
        assertThat(group.receita()).isEqualByComparingTo("42.50");
        assertThat(group.permanenciaMediaMin()).isEqualTo(60L);
    }
}
