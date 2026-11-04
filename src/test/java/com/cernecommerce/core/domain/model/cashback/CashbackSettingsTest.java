package com.cernecommerce.core.domain.model.cashback;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashbackSettingsTest {

    @Test
    void defaults_matchTheSeededValues() {
        CashbackSettings settings = CashbackSettings.defaults();

        assertThat(settings.carenciaDias()).isEqualTo(7);
        assertThat(settings.expiracaoDias()).isEqualTo(180);
        assertThat(settings.resgateTetoPercent()).isEqualByComparingTo("50");
        assertThat(settings.resgateMinimoSaldo()).isEqualByComparingTo("50.00");
        assertThat(settings.redemptionEnabled()).isTrue();
    }

    @Test
    void zeroCap_disablesRedemption() {
        assertThat(new CashbackSettings(0, 1, BigDecimal.ZERO, BigDecimal.ZERO).redemptionEnabled()).isFalse();
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThatThrownBy(() -> new CashbackSettings(-1, 180, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("carenciaDias");
        assertThatThrownBy(() -> new CashbackSettings(7, 0, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expiracaoDias");
        assertThatThrownBy(() -> new CashbackSettings(7, 180, new BigDecimal("100.01"), BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resgateTetoPercent");
        assertThatThrownBy(() -> new CashbackSettings(7, 180, BigDecimal.TEN, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resgateMinimoSaldo");
    }
}
