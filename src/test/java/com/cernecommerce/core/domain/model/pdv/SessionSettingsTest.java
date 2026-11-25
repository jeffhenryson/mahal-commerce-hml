package com.cernecommerce.core.domain.model.pdv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionSettingsTest {

    @Test
    void promoDays_roundTripThroughTheirTextForm() {
        SessionSettings s = new SessionSettings(" vaso_p ", "VASO_G", new BigDecimal("10.00"),
                Set.of(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY));

        assertThat(s.vasoPadraoCodigo()).isEqualTo("VASO_P");
        assertThat(s.diasDuploRoshAsText()).isEqualTo("WEDNESDAY,SUNDAY");
        assertThat(SessionSettings.parseDias(s.diasDuploRoshAsText()))
                .containsExactlyInAnyOrder(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY);
        assertThat(SessionSettings.parseDias("")).isEmpty();
        assertThat(s.isDuploRoshDay(DayOfWeek.WEDNESDAY)).isTrue();
        assertThat(s.isDuploRoshDay(DayOfWeek.MONDAY)).isFalse();
    }

    @Test
    void negativeUpgradePrice_isRejected() {
        assertThatThrownBy(() -> new SessionSettings("VASO_P", "VASO_G", new BigDecimal("-1"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tier_validatesAndExposesTheSyntheticSku() {
        SessionTier tier = new SessionTier(3L, " Sence ", new BigDecimal("40.00"), " ", 3, true);

        assertThat(tier.nome()).isEqualTo("Sence");
        assertThat(tier.marcas()).isNull();
        assertThat(tier.sku()).isEqualTo("SESS-3");
        assertThatThrownBy(() -> SessionTier.create("X", new BigDecimal("-1"), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promoEssenciaPreco_padraoDez_eRecusaZero() {
        assertThat(SessionSettings.defaults().promoEssenciaPreco()).isEqualByComparingTo("10.00");
        assertThat(new SessionSettings("VASO_P", "VASO_G", BigDecimal.ZERO, Set.of(), BigDecimal.ZERO, null, null,
                new BigDecimal("12.00")).promoEssenciaPreco()).isEqualByComparingTo("12.00");
        assertThatThrownBy(() -> new SessionSettings("VASO_P", "VASO_G", BigDecimal.ZERO, Set.of(), BigDecimal.ZERO,
                null, null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
