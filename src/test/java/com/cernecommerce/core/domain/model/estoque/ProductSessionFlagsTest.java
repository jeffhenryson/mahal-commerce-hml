package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PDV-F061/PDV-F062 — marcações das modalidades da sessão: padrão de produto novo e herança da variação. */
class ProductSessionFlagsTest {

    private static Product essencia(ProductVariant... variantes) {
        return Product.of(1L, "ZOMO", "Zomo", "essencia", true, List.of(variantes));
    }

    private static long nextId = 10;

    private static ProductVariant sabor(String sku) {
        return ProductVariant.of(nextId++, sku, List.of(new ProductAttribute("sabor", sku)), true);
    }

    @Test
    void produtoNovo_valeEmTodasAsModalidades() {
        Product novo = Product.create("ZOMO", "Zomo", "essencia", List.of());

        assertThat(novo.sessionFlags()).isEqualTo(new SessionModalityFlags(true, true, true));
    }

    @Test
    void variacaoSemMarcacao_herdaDoProduto() {
        Product p = essencia(sabor("ZOMO-UVA"))
                .withSessionFlags(new SessionModalityFlags(true, true, true));

        assertThat(p.sessionFlagsFor("ZOMO-UVA")).isEqualTo(new SessionModalityFlags(true, true, true));
    }

    @Test
    void variacaoFalse_excluiSoEla() {
        Product p = essencia(sabor("ZOMO-UVA").withSessionFlags(false, null, null), sabor("ZOMO-MENTA"))
                .withSessionFlags(new SessionModalityFlags(true, false, true));

        assertThat(p.sessionFlagsFor("ZOMO-UVA").compraEssencia()).isFalse();
        assertThat(p.sessionFlagsFor("ZOMO-MENTA").compraEssencia()).isTrue();
        assertThat(p.sessionFlagsFor("ZOMO").compraEssencia()).isTrue();
    }

    @Test
    void foraDaPromo_naVariacao_excluiSoEla_eSemMarcacaoHerda() {
        Product p = essencia(sabor("ZOMO-UVA").withSessionFlags(null, null, false), sabor("ZOMO-MENTA"))
                .withSessionFlags(new SessionModalityFlags(true, true, true));

        assertThat(p.sessionFlagsFor("ZOMO-UVA").promoEssencia()).isFalse();
        assertThat(p.sessionFlagsFor("ZOMO-UVA").compraEssencia()).isTrue();
        assertThat(p.sessionFlagsFor("ZOMO-MENTA").promoEssencia()).isTrue();
    }

    @Test
    void produtoForaDaPromo_variacaoSemMarcacaoHerdaFora() {
        Product p = essencia(sabor("ZOMO-UVA"))
                .withSessionFlags(new SessionModalityFlags(true, true, false));

        assertThat(p.sessionFlagsFor("ZOMO-UVA").promoEssencia()).isFalse();
    }
}
