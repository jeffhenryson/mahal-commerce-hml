package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestKeysTest {

    @Test
    void normaliza_acento_caixa_e_pontuacao() {
        assertThat(ProductRequestKeys.normalize("Rosh  Kaloud-Lotus", "Kaloud", null))
                .isEqualTo("rosh kaloud lotus kaloud");
        assertThat(ProductRequestKeys.normalizeText("Essência MAÇÃ")).isEqualTo("essencia maca");
        assertThat(ProductRequestKeys.normalizeText(null)).isEmpty();
    }

    @Test
    void iguais_tem_similaridade_1_e_disjuntos_0() {
        assertThat(ProductRequestKeys.similarity("ziggy love", "ziggy love")).isEqualTo(1.0);
        assertThat(ProductRequestKeys.similarity("ziggy", "kaloud")).isZero();
        assertThat(ProductRequestKeys.similarity("", "ziggy")).isZero();
    }

    @Test
    void variacao_de_escrita_passa_do_limiar_e_produto_diferente_nao() {
        String existing = ProductRequestKeys.normalize("Essência Ziggy Love 66", "Ziggy", "50g");
        String typo = ProductRequestKeys.normalize("essencia zigy love", null, null);
        String other = ProductRequestKeys.normalize("Rosh Kaloud Lotus", null, null);
        assertThat(ProductRequestKeys.similarity(typo, existing))
                .isGreaterThanOrEqualTo(ProductRequestKeys.SIMILARITY_THRESHOLD);
        assertThat(ProductRequestKeys.similarity(other, existing))
                .isLessThan(ProductRequestKeys.SIMILARITY_THRESHOLD);
    }

    @Test
    void trigramas_como_o_pg_trgm() {
        // pg_trgm: "cat" → "  c", " ca", "cat", "at "
        assertThat(ProductRequestKeys.trigrams("cat")).containsExactlyInAnyOrder("  c", " ca", "cat", "at ");
    }
}
