package com.cernecommerce.adapter.in.assistente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiSanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "o CPF é 123.456.789-09",
            "cpf 12345678909 do cliente",
            "CNPJ 12.345.678/0001-90",
            "manda para joao.silva+loja@gmail.com",
            "liga no (11) 98765-4321",
            "whats +55 11 98765-4321",
            "telefone 11987654321",
            "CEP 01310-100",
            "mora na Rua das Flores, 123",
            "entrega na Av. Paulista 1578"
    })
    void remove_dado_pessoal(String texto) {
        String limpo = PiiSanitizer.limpar(texto);
        assertThat(limpo).contains("removido]");
        assertThat(limpo).doesNotContainPattern("\\d{4}-\\d{4}|\\d{3}\\.\\d{3}\\.\\d{3}|@gmail|\\d{11}|Flores, 123|Paulista 1578");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "quais os 5 produtos mais vendidos esta semana?",
            "vendas de 2026-11-01 a 2026-11-09",
            "faturamento de R$ 1.234,56 no dia 09/11/2026",
            "SKU ESS-ZOMO-50 e código de barras 7891234567895",
            "pedido PED-20261109-0042",
            "estoque do SKU 12345678 está baixo",
            "crie a marca Zomo"
    })
    void preserva_o_que_nao_e_dado_pessoal(String texto) {
        assertThat(PiiSanitizer.limpar(texto)).isEqualTo(texto);
    }

    @Test
    void nulo_e_vazio_passam_direto() {
        assertThat(PiiSanitizer.limpar(null)).isNull();
        assertThat(PiiSanitizer.limpar("")).isEmpty();
    }
}
