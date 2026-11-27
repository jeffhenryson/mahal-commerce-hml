package com.cernecommerce.adapter.in.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestDTOConverterTest {

    @Test
    void mascara_celular_fixo_e_ddi() {
        assertThat(ProductRequestDTOConverter.maskPhone("85999991234")).isEqualTo("(85) 9****-1234");
        assertThat(ProductRequestDTOConverter.maskPhone("8533331234")).isEqualTo("(85) 3***-1234");
        assertThat(ProductRequestDTOConverter.maskPhone("5585999991234")).isEqualTo("(85) 9****-1234");
    }

    @Test
    void sem_telefone_ou_curto_demais() {
        assertThat(ProductRequestDTOConverter.maskPhone(null)).isNull();
        assertThat(ProductRequestDTOConverter.maskPhone("1234")).isEqualTo("****");
    }
}
