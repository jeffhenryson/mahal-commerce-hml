package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** EST-F032 — a ligação "a embalagem pai contém N do filho", sem banco. */
class SkuPackagingTest {

    @Test
    void ligacao_valida_guardaOsDoisLadosEOFator() {
        SkuPackaging maco = new SkuPackaging("LM-AZUL-UN", "LM-AZUL-MACO", 20);

        assertThat(maco.childSku()).isEqualTo("LM-AZUL-UN");
        assertThat(maco.parentSku()).isEqualTo("LM-AZUL-MACO");
        assertThat(maco.unitsPerParent()).isEqualTo(20);
    }

    /**
     * Quantas embalagens pai abrir para cobrir a falta: sempre para CIMA — não se abre meio maço.
     * Faltando 3 cigarros, abre-se 1 maço; faltando 21, abrem-se 2.
     */
    @Test
    void parentsToOpen_arredondaParaCima() {
        SkuPackaging maco = new SkuPackaging("LM-AZUL-UN", "LM-AZUL-MACO", 20);

        assertThat(maco.parentsToOpen(new BigDecimal("3"))).isEqualByComparingTo("1");
        assertThat(maco.parentsToOpen(new BigDecimal("20"))).isEqualByComparingTo("1");
        assertThat(maco.parentsToOpen(new BigDecimal("21"))).isEqualByComparingTo("2");
    }

    @Test
    void childUnits_multiplicaPeloFator() {
        assertThat(new SkuPackaging("LM-AZUL-MACO", "LM-AZUL-CART", 10).childUnits(new BigDecimal("2")))
                .isEqualByComparingTo("20");
    }

    /** Fator 1 não é embalagem, é o mesmo item com outro nome; zero e negativo não existem. */
    @Test
    void fator_menorOuIgualAUm_eRecusado() {
        assertThatThrownBy(() -> new SkuPackaging("LM-AZUL-UN", "LM-AZUL-MACO", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unitsPerParent");
        assertThatThrownBy(() -> new SkuPackaging("LM-AZUL-UN", "LM-AZUL-MACO", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void embalagemDeSiMesma_eRecusada() {
        assertThatThrownBy(() -> new SkuPackaging("LM-AZUL-UN", "LM-AZUL-UN", 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("si mesmo");
    }

    @Test
    void skus_saoObrigatorios() {
        assertThatThrownBy(() -> new SkuPackaging(" ", "LM-AZUL-MACO", 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkuPackaging("LM-AZUL-UN", null, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
