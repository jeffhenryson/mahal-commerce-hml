package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.model.estoque.QuantityPromotionEngine.CartLine;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotionEngine.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesmos números de {@code frontend-admin/src/app/features/estoque/promocao-quantidade.models.spec.ts}:
 * front e servidor têm que dar o mesmo desconto, senão a venda volta 409 PROMOTION_CHANGED.
 */
class QuantityPromotionEngineTest {

    private static final Map<String, BigDecimal> PRECOS = Map.of(
            "SEDA-A", bd("3.50"), "SEDA-B", bd("4.00"), "ISQ", bd("5.00"), "SEDA-C", bd("2.00"),
            "X1", bd("3.33"), "X2", bd("3.34"));

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static QuantityPromotion promo(long id, int quantity, String bundle, String... skus) {
        return new QuantityPromotion(id, "Promo " + id, null, quantity, bd(bundle), List.of(skus),
                Set.of(PromotionChannel.PDV, PromotionChannel.COMANDA), true, null, null, null, null, null, null);
    }

    private static QuantityPromotion seda() {
        return promo(1, 4, "10", "SEDA-A");
    }

    private static List<CartLine> carrinho(Object... skuQtd) {
        List<CartLine> lines = new ArrayList<>();
        for (int i = 0; i < skuQtd.length; i += 2) {
            String sku = (String) skuQtd[i];
            lines.add(new CartLine(sku, BigDecimal.valueOf((Integer) skuQtd[i + 1]), PRECOS.get(sku)));
        }
        return lines;
    }

    @Test
    void tresUnidadesNaoFecham() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-A", 3), List.of(seda()), Map.of());
        assertThat(r.total()).isEqualByComparingTo("0");
        assertThat(r.applied()).isEmpty();
    }

    @Test
    void quatroUnidades14Viram10() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-A", 4), List.of(seda()), Map.of());
        assertThat(r.total()).isEqualByComparingTo("4.00");
        assertThat(r.applied()).singleElement().satisfies(a -> {
            assertThat(a.groups()).isEqualTo(1);
            assertThat(a.regularPrice()).isEqualByComparingTo("14.00");
            assertThat(a.promotionId()).isEqualTo(1L);
        });
        assertThat(r.discountFor("SEDA-A")).isEqualByComparingTo("4.00");
    }

    @Test
    void cincoUnidadesUmGrupoEAQuintaNormal() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-A", 5), List.of(seda()), Map.of());
        assertThat(r.total()).isEqualByComparingTo("4.00");
        assertThat(r.applied().get(0).lines().get(0).units()).isEqualTo(4);
    }

    @Test
    void oitoUnidadesDoisGrupos() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-A", 8), List.of(seda()), Map.of());
        assertThat(r.applied().get(0).groups()).isEqualTo(2);
        assertThat(r.total()).isEqualByComparingTo("8.00");
    }

    @Test
    void mixAndMatchPegaAsMaisCarasERateiaProporcional() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-A", 3, "SEDA-B", 2),
                List.of(promo(1, 4, "10", "SEDA-A", "SEDA-B")), Map.of());
        assertThat(r.total()).isEqualByComparingTo("5.00");
        var lines = r.applied().get(0).lines();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).sku()).isEqualTo("SEDA-B");
        assertThat(lines.get(0).units()).isEqualTo(2);
        assertThat(lines.get(0).discount()).isEqualByComparingTo("2.67");
        assertThat(lines.get(1).sku()).isEqualTo("SEDA-A");
        assertThat(lines.get(1).units()).isEqualTo(2);
        assertThat(lines.get(1).discount()).isEqualByComparingTo("2.33");
    }

    @Test
    void nuncaEncarece() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-C", 4),
                List.of(promo(1, 4, "10", "SEDA-C")), Map.of());
        assertThat(r.total()).isEqualByComparingTo("0");
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    void unidadesPresasAKitNaoContam() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-A", 5), List.of(seda()), Map.of("SEDA-A", 2));
        assertThat(r.total()).isEqualByComparingTo("0");
    }

    @Test
    void cadaUnidadeEmUmaPromocaoSoNaOrdemDoId() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-A", 4),
                List.of(promo(2, 4, "9", "SEDA-A"), promo(1, 4, "10", "SEDA-A")), Map.of());
        assertThat(r.applied()).singleElement().extracting(QuantityPromotionEngine.Applied::promotionId)
                .isEqualTo(1L);
        assertThat(r.total()).isEqualByComparingTo("4.00");
    }

    @Test
    void ignoraSkuForaDaRegra() {
        Result r = QuantityPromotionEngine.apply(carrinho("ISQ", 4), List.of(seda()), Map.of());
        assertThat(r.total()).isEqualByComparingTo("0");
    }

    @Test
    void arredondaAoCentavoEFechaASomaNoUltimoSku() {
        Result r = QuantityPromotionEngine.apply(carrinho("X1", 2, "X2", 1),
                List.of(promo(1, 3, "9.99", "X1", "X2")), Map.of());
        assertThat(r.total()).isEqualByComparingTo("0.01");
        BigDecimal soma = r.applied().get(0).lines().stream()
                .map(QuantityPromotionEngine.LineDiscount::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("0.01");
    }

    @Test
    void linhasRepetidasDoMesmoSkuSomam() {
        Result r = QuantityPromotionEngine.apply(carrinho("SEDA-A", 2, "SEDA-A", 2), List.of(seda()), Map.of());
        assertThat(r.total()).isEqualByComparingTo("4.00");
    }

    @Test
    void semRegraOuCarrinhoVazioDevolveVazio() {
        assertThat(QuantityPromotionEngine.apply(List.of(), List.of(seda()), Map.of()).isEmpty()).isTrue();
        assertThat(QuantityPromotionEngine.apply(carrinho("SEDA-A", 4), List.of(), Map.of()).isEmpty()).isTrue();
    }
}
