package com.cernecommerce.adapter.out.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceLabelTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36|Chrome no Windows",
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/126.0 Safari/537.36 Edg/126.0|Edge no Windows",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Version/17.5 Mobile Safari/604.1|Safari no iPhone",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36|Chrome no Android",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.5; rv:127.0) Gecko/20100101 Firefox/127.0|Firefox no Mac",
            "Firefox|Firefox"
    })
    void reconhece_navegador_e_sistema(String userAgent, String esperado) {
        assertThat(DeviceLabel.of(userAgent)).isEqualTo(esperado);
    }

    @Test
    void user_agent_desconhecido_omite_a_linha() {
        assertThat(DeviceLabel.of("curl/8.5.0")).isNull();
        assertThat(DeviceLabel.of(null)).isNull();
    }
}
