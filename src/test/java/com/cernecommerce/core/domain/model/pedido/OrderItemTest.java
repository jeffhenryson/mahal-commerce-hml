package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.exception.pedido.ItemDiscountExceedsGrossException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    private static final BigDecimal TWO = new BigDecimal("2.000");

    /** Carvão do exemplo do plano: custo 18,00, venda 22,00 — margem de 18,2%. */
    private static Pricing carvao() {
        return Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00"));
    }

    @Test
    void fromCatalog_freezesPriceAndCostFromPricing() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), null);

        assertThat(item.id()).isNull();
        assertThat(item.unitPrice()).isEqualByComparingTo("22.00");
        assertThat(item.costPrice()).isEqualByComparingTo("18.00");
        assertThat(item.discountAmount()).isEqualByComparingTo("0");
        assertThat(item.cashbackPercent()).isNull();
        assertThat(item.productName()).as("sobrecarga de 4 argumentos não carimba nome").isNull();
    }

    /** productName congelado no instante da venda — BACKEND_TODO.md do mahal-admin. */
    @Test
    void fromCatalog_5Argumentos_freezesProductName() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), null, "Carvão em Barra");

        assertThat(item.productName()).isEqualTo("Carvão em Barra");
        assertThat(item.unitPrice()).isEqualByComparingTo("22.00");
    }

    @Test
    void withDiscountAndWithCashbackPercent_preserveProductName() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), null, "Carvão em Barra")
                .withDiscount(new BigDecimal("2.00"))
                .withCashbackPercent(new BigDecimal("3.00"));

        assertThat(item.productName()).isEqualTo("Carvão em Barra");
    }

    @Test
    void fromCatalog_usesSuggestedPriceWhenThereIsNoSalePrice() {
        // Só custo e markup: o preço efetivo vem da sugestão, e é ele que congela no item.
        OrderItem item = OrderItem.fromCatalog("ESS-001", BigDecimal.ONE,
                Pricing.byMarkup(new BigDecimal("25.00"), new BigDecimal("200")), null);

        assertThat(item.unitPrice()).isEqualByComparingTo("75.00");
    }

    @Test
    void fromCatalog_rejectsProductWithoutPrice() {
        assertThatThrownBy(() -> OrderItem.fromCatalog("SEM-PRECO", BigDecimal.ONE, Pricing.empty(), null))
                .isInstanceOf(ProductNotPricedException.class)
                .hasMessageContaining("SEM-PRECO");
    }

    @Test
    void fromCatalog_rejectsNullPricingInsteadOfSellingForFree() {
        assertThatThrownBy(() -> OrderItem.fromCatalog("SEM-PRECO", BigDecimal.ONE, null, null))
                .isInstanceOf(ProductNotPricedException.class);
    }

    @Test
    void fromCatalog_acceptsProductPricedAtZero() {
        // Preço zero é uma decisão comercial (brinde); preço desconhecido é cadastro incompleto.
        OrderItem item = OrderItem.fromCatalog("BRINDE", BigDecimal.ONE,
                Pricing.of(null, null, BigDecimal.ZERO), null);

        assertThat(item.unitPrice()).isEqualByComparingTo("0");
        assertThat(item.grossAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void grossAndNetAmount_discountReducesOnlyTheNet() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), new BigDecimal("4.00"));

        assertThat(item.grossAmount()).isEqualByComparingTo("44.00");
        assertThat(item.netAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void marginAmount_discountsCostOfEveryUnit() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), null);

        // 44,00 de venda - 36,00 de custo (18,00 x 2)
        assertThat(item.marginAmount()).isEqualByComparingTo("8.00");
    }

    @Test
    void marginAmount_isNegativeWhenSellingBelowCost() {
        // Vender abaixo do custo é permitido — queima de estoque e produto-isca são legítimos.
        OrderItem item = OrderItem.fromCatalog("ISCA", BigDecimal.ONE,
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("10.00")), null);

        assertThat(item.marginAmount()).isEqualByComparingTo("-8.00");
    }

    @Test
    void cashbackAmount_appliesStampedRateOverTheNet() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), new BigDecimal("4.00"))
                .withCashbackPercent(new BigDecimal("8.0000"));

        // 8% sobre o líquido de 40,00 — não sobre o bruto de 44,00.
        assertThat(item.cashbackAmount()).isEqualByComparingTo("3.20");
    }

    @Test
    void cashbackAmount_isNullWhileTheRateHasNotBeenStamped() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), null);

        assertThat(item.cashbackAmount()).isNull();
    }

    @Test
    void legacyItemWithoutFrozenPrice_returnsNullInsteadOfLying() {
        // Pedido anterior à migração: um default zero mentiria sobre a margem.
        OrderItem legacy = OrderItem.of(1L, "CARV-001", TWO, null, null, null, null);

        assertThat(legacy.grossAmount()).isNull();
        assertThat(legacy.netAmount()).isNull();
        assertThat(legacy.marginAmount()).isNull();
        assertThat(legacy.cashbackAmount()).isNull();
    }

    @Test
    void rejectsDiscountGreaterThanGross() {
        // PDV-C016 — a regra é a mesma de sempre; o que mudou foi o TIPO. Vindo por fromCatalog o
        // valor veio do cliente HTTP, e IllegalArgumentException vira um 400 genérico que descarta
        // a mensagem — indistinguível de qualquer corpo malformado.
        assertThatThrownBy(() -> OrderItem.fromCatalog("CARV-001", TWO, carvao(), new BigDecimal("44.01")))
                .isInstanceOf(ItemDiscountExceedsGrossException.class)
                .hasMessageContaining("CARV-001");
    }

    /**
     * PDV-C016 — a invariante do record continua de pé, para o caminho de <b>reconstituição</b>:
     * {@code of} lê de persistência, e ali um desconto acima do bruto é dado corrompido ou erro de
     * programação, não payload de cliente. É a rede de segurança que a exceção tipada não
     * substitui.
     */
    @Test
    void of_stillRejectsDiscountGreaterThanGrossAsAnInvariant() {
        assertThatThrownBy(() -> OrderItem.of(1L, "CARV-001", TWO, new BigDecimal("22.00"),
                new BigDecimal("18.00"), new BigDecimal("44.01"), null, "Carvão"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountAmount");
    }

    @Test
    void acceptsDiscountEqualToGross() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), new BigDecimal("44.00"));

        assertThat(item.netAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsNegativeDiscount() {
        assertThatThrownBy(() -> OrderItem.fromCatalog("CARV-001", TWO, carvao(), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSkuAndNonPositiveQuantity() {
        assertThatThrownBy(() -> OrderItem.fromCatalog("  ", TWO, carvao(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderItem.fromCatalog("CARV-001", BigDecimal.ZERO, carvao(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderItem.fromCatalog("CARV-001", new BigDecimal("-1"), carvao(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCashbackPercentOutOfRange() {
        OrderItem item = OrderItem.fromCatalog("CARV-001", TWO, carvao(), null);

        assertThatThrownBy(() -> item.withCashbackPercent(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.withCashbackPercent(new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withDiscountAndWithCashbackPercent_returnCopiesAndKeepTheOriginalIntact() {
        OrderItem original = OrderItem.fromCatalog("CARV-001", TWO, carvao(), null);

        OrderItem discounted = original.withDiscount(new BigDecimal("4.00"));
        OrderItem stamped = original.withCashbackPercent(new BigDecimal("2.5000"));

        assertThat(original.discountAmount()).isEqualByComparingTo("0");
        assertThat(original.cashbackPercent()).isNull();
        assertThat(discounted.netAmount()).isEqualByComparingTo("40.00");
        assertThat(stamped.cashbackPercent()).isEqualByComparingTo("2.5000");
    }

    /** Fora dos benefícios: a taxa de cashback zera, antes ou depois de carimbada. */
    @Test
    void withBenefitsExcluded_zeraATaxaDeCashback_eOCarimboDepoisNaoReativa() {
        OrderItem item = OrderItem.of(1L, "CIG", BigDecimal.ONE, new BigDecimal("50.00"), null, null,
                new BigDecimal("5"));

        OrderItem excluido = item.withBenefitsExcluded(true);
        assertThat(excluido.benefitsExcluded()).isTrue();
        assertThat(excluido.cashbackAmount()).isEqualByComparingTo("0");
        assertThat(excluido.withCashbackPercent(new BigDecimal("10")).cashbackPercent()).isEqualByComparingTo("0");
        assertThat(excluido.withNotes("x").benefitsExcluded()).isTrue();
        assertThat(item.withBenefitsExcluded(false).cashbackPercent()).isEqualByComparingTo("5");
    }
}
