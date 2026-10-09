package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComandaItemTest {

    private static final Pricing PRICED = Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00"));

    @Test
    void fromCatalog_freezesPriceAndCostFromPricing() {
        ComandaItem item = ComandaItem.fromCatalog("ESS-MENTA", BigDecimal.ONE, PRICED, "Essência Menta");

        assertThat(item.id()).isNull();
        assertThat(item.sku()).isEqualTo("ESS-MENTA");
        assertThat(item.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(item.costPrice()).isEqualByComparingTo("10.00");
        assertThat(item.productName()).isEqualTo("Essência Menta");
        assertThat(item.addedAt()).isNotNull();
    }

    @Test
    void fromCatalog_rejectsProductWithoutPrice() {
        assertThatThrownBy(() -> ComandaItem.fromCatalog("SEM-PRECO", BigDecimal.ONE, Pricing.empty(), null))
                .isInstanceOf(ProductNotPricedException.class);
    }

    @Test
    void fromCatalog_rejectsNullPricingInsteadOfSellingForFree() {
        assertThatThrownBy(() -> ComandaItem.fromCatalog("SKU", BigDecimal.ONE, null, null))
                .isInstanceOf(ProductNotPricedException.class);
    }

    @Test
    void subtotal_isQuantityTimesUnitPrice() {
        ComandaItem item = ComandaItem.fromCatalog("ESS-MENTA", new BigDecimal("2"), PRICED, null);

        assertThat(item.subtotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void rejectsBlankSkuAndNonPositiveQuantity() {
        assertThatThrownBy(() -> ComandaItem.of(1L, " ", BigDecimal.ONE, BigDecimal.TEN, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sku");
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ZERO, BigDecimal.TEN, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
    }

    @Test
    void rejectsMissingUnitPriceAndAddedAt() {
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ONE, null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unitPrice");
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ONE, BigDecimal.TEN, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("addedAt");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant addedAt = Instant.parse("2026-08-18T20:00:00Z");
        ComandaItem item = ComandaItem.of(9L, "ESS-MENTA", new BigDecimal("2"), new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", addedAt);

        assertThat(item.id()).isEqualTo(9L);
        assertThat(item.addedAt()).isEqualTo(addedAt);
    }
    // ── Sessão de narguilé (PDV-F010) ────────────────────────────────────────────────────────

    /**
     * O ponto que a feature inteira gira em torno: em {@code OPEN_ROSH} o preço vem do
     * {@code openRoshPrice} do produto <b>pai</b>, e não do {@code pricing} do SKU da variação do
     * sabor — que aqui cobraria 25 no lugar de 60.
     */
    @Test
    void forSession_takesUnitPriceFromTheCallerButCostFromTheCatalog() {
        ComandaItem item = ComandaItem.forSession("SESS-BLUE", BigDecimal.ONE, new BigDecimal("60.00"),
                PRICED, "Sessão Blueberry", ConsumptionMode.OPEN_ROSH, false, null, null, null);

        assertThat(item.unitPrice()).isEqualByComparingTo("60.00");
        assertThat(item.costPrice()).isEqualByComparingTo("10.00");
        assertThat(item.mode()).isEqualTo(ConsumptionMode.OPEN_ROSH);
        assertThat(item.courtesy()).isFalse();
    }

    /**
     * Cortesia não é linha grátis para a contabilidade: o custo é congelado normalmente, e é ele
     * que faz a margem do pedido mostrar o prejuízo real da promo e do open rosh. Custo nulo ou
     * zerado aqui mentiria sobre a pergunta de negócio por trás da feature.
     */
    @Test
    void forSession_courtesyIsFreeForTheCustomerButNotForTheMargin() {
        ComandaItem cortesia = ComandaItem.forSession("SESS-UVA", BigDecimal.ONE, BigDecimal.ZERO,
                PRICED, "Sessão Uva", ConsumptionMode.SABOR_EXTRA, true, 7L, null, null);

        assertThat(cortesia.unitPrice()).isEqualByComparingTo("0");
        assertThat(cortesia.subtotal()).isEqualByComparingTo("0.00");
        assertThat(cortesia.costPrice()).isEqualByComparingTo("10.00");
        assertThat(cortesia.courtesy()).isTrue();
        assertThat(cortesia.linkedItemId()).isEqualTo(7L);
    }

    /** A checagem de preço vale mesmo em cortesia — é justamente para não gravar custo nulo. */
    @Test
    void forSession_rejectsUnpricedProductEvenForACourtesyLine() {
        assertThatThrownBy(() -> ComandaItem.forSession("SEM-PRECO", BigDecimal.ONE, BigDecimal.ZERO,
                Pricing.empty(), null, ConsumptionMode.TROCA, true, 7L, null, null))
                .isInstanceOf(ProductNotPricedException.class);
    }

    @Test
    void courtesyMustCostZero() {
        // Espelha o CHECK ck_comanda_item_courtesy_is_free: cortesia é preço zero por definição.
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ONE, new BigDecimal("25.00"), null,
                null, Instant.now(), ConsumptionMode.SABOR_EXTRA, true, 7L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cortesia");
    }

    @Test
    void linkedItemIdOnlyMakesSenseForSaborExtraAndTroca() {
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ONE, new BigDecimal("25.00"), null,
                null, Instant.now(), ConsumptionMode.OPEN_ROSH, false, 7L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("linkedItemId");
    }

    /** Linha anterior a PDV-F010 lê como {@code NORMAL}, nunca com modo nulo. */
    @Test
    void of_legacyRowReadsAsNormal() {
        ComandaItem legado = ComandaItem.of(9L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", Instant.now(), null, false, null);

        assertThat(legado.mode()).isEqualTo(ConsumptionMode.NORMAL);
    }

    // ── PDV-F011 ─────────────────────────────────────────────────────────────────────────────

    /**
     * O acréscimo chega JÁ SOMADO em unitPrice e é guardado à parte só para o relatório separar as
     * parcelas depois — não dá para reconstruí-lo do total. Mesma lógica de
     * {@code OrderItem.discountAmount} ser campo próprio em vez de virar um preço menor.
     */
    @Test
    void forSession_keepsSurchargeAsItsOwnFieldWhileUnitPriceIsAlreadyTheSum() {
        ComandaItem item = ComandaItem.forSession("SESS-BLUE", BigDecimal.ONE, new BigDecimal("75.00"),
                PRICED, "Sessão Blueberry", ConsumptionMode.OPEN_ROSH, false, null,
                "Narguilé grande · Pinça P-02", new BigDecimal("15.00"));

        assertThat(item.unitPrice()).isEqualByComparingTo("75.00");
        assertThat(item.surchargeAmount()).isEqualByComparingTo("15.00");
        assertThat(item.subtotal()).isEqualByComparingTo("75.00");
        assertThat(item.notes()).isEqualTo("Narguilé grande · Pinça P-02");
        // Acréscimo é margem, não custo.
        assertThat(item.costPrice()).isEqualByComparingTo("10.00");
    }

    /** Espelha ck_comanda_item_surcharge_not_on_courtesy: linha que não se paga não se acresce. */
    @Test
    void surchargeIsRejectedOnACourtesyLine() {
        assertThatThrownBy(() -> ComandaItem.of(1L, "SESS-UVA", BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("10.00"), "Sessão Uva", Instant.now(), ConsumptionMode.TROCA, true, 7L,
                null, new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cortesia");
    }

    /** Espelha ck_comanda_item_surcharge_only_open_rosh. */
    @Test
    void surchargeIsRejectedOutsideOpenRosh() {
        assertThatThrownBy(() -> ComandaItem.of(1L, "SESS-UVA", BigDecimal.ONE, new BigDecimal("45.00"),
                new BigDecimal("10.00"), "Sessão Uva", Instant.now(), ConsumptionMode.SABOR_EXTRA, false,
                7L, null, new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPEN_ROSH");
    }

    /** Espelha ck_comanda_item_surcharge_non_negative. */
    @Test
    void surchargeCannotBeNegative() {
        assertThatThrownBy(() -> ComandaItem.of(1L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("60.00"),
                new BigDecimal("10.00"), "Sessão Blueberry", Instant.now(), ConsumptionMode.OPEN_ROSH,
                false, null, null, new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    /** Zero não é acréscimo: passa em qualquer modo, sem acionar nenhuma das duas invariantes. */
    @Test
    void zeroSurchargeIsAllowedAnywhere() {
        ComandaItem item = ComandaItem.of(1L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", Instant.now(), ConsumptionMode.NORMAL, false,
                null, null, BigDecimal.ZERO);

        assertThat(item.surchargeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void notesAboveTheColumnLimitAreRejected() {
        String longa = "x".repeat(ComandaItem.NOTES_MAX_LENGTH + 1);
        assertThatThrownBy(() -> ComandaItem.of(1L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", Instant.now(), ConsumptionMode.NORMAL, false,
                null, longa, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(ComandaItem.NOTES_MAX_LENGTH));
    }

    /** Linha anterior à V116 lê os dois como null — que é a verdade, e não "não teve". */
    @Test
    void legacyLineReadsNotesAndSurchargeAsNull() {
        ComandaItem legado = ComandaItem.of(1L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", Instant.now());

        assertThat(legado.notes()).isNull();
        assertThat(legado.surchargeAmount()).isNull();
    }

    // ── Essência do catálogo na sessão (PDV-F042) ────────────────────────────────────────────

    private static ComandaItem sessaoDoCardapio() {
        return ComandaItem.forMenuSession("SESS-2", new BigDecimal("30.00"), "Sessão Premium",
                ConsumptionMode.SESSAO, false, null, "Zomo Blueberry", SessionProgress.awaitingPayment(), null);
    }

    /**
     * A linha do cardápio tem SKU sintético: sem a essência carimbada, não há o que desfazer no
     * estoque, e é por isso que ela não "consumiu estoque".
     */
    @Test
    void sessaoDoCardapio_semEssencia_naoConsumiuEstoque() {
        ComandaItem linha = sessaoDoCardapio();

        assertThat(linha.essenceSku()).isNull();
        assertThat(linha.consumedStock()).isFalse();
        assertThat(linha.stockSku()).isEqualTo("SESS-2");
    }

    /** Com a essência, o SKU de estoque da linha passa a ser o do sabor, não o da faixa. */
    @Test
    void sessaoDoCardapio_comEssencia_consumiuEstoqueDoSabor() {
        ComandaItem linha = sessaoDoCardapio().withEssence("ZGY-BLUEBERRY");

        assertThat(linha.essenceSku()).isEqualTo("ZGY-BLUEBERRY");
        assertThat(linha.consumedStock()).isTrue();
        assertThat(linha.stockSku()).isEqualTo("ZGY-BLUEBERRY");
        assertThat(linha.sku()).isEqualTo("SESS-2");
    }

    /** A essência sobrevive às transições da linha — pagar e cobrar não podem apagá-la. */
    @Test
    void essencia_sobreviveAsCopiasDaLinha() {
        ComandaItem linha = sessaoDoCardapio().withEssence("ZGY-BLUEBERRY").withPackageCounter(3, 5)
                .withSessionPaid(Instant.now()).closedIn(77L);

        assertThat(linha.essenceSku()).isEqualTo("ZGY-BLUEBERRY");
        assertThat(linha.consumedPackage()).isTrue();
    }

    /** Linha de catálogo já é o próprio produto: consumiu estoque do próprio SKU, sem essência. */
    @Test
    void linhaDeCatalogo_consumiuEstoqueDoProprioSku() {
        ComandaItem linha = ComandaItem.fromCatalog("CARVAO-1KG", BigDecimal.ONE, PRICED, "Carvão");

        assertThat(linha.consumedStock()).isTrue();
        assertThat(linha.stockSku()).isEqualTo("CARVAO-1KG");
    }

    /** Essência só existe em linha do cardápio de sessão — espelha o CHECK da V147. */
    @Test
    void essencia_emLinhaDeCatalogo_eRecusada() {
        ComandaItem linha = ComandaItem.fromCatalog("CARVAO-1KG", BigDecimal.ONE, PRICED, "Carvão");

        assertThatThrownBy(() -> linha.withEssence("ZGY-BLUEBERRY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("essenceSku");
    }

    /** Saiu da espera, a essência queimou: preparando, entregue, recolhida. Na espera e na fila, não. */
    @Test
    void essenceBurned_soDepoisDeSairDaEspera() {
        Instant t = Instant.now();
        ComandaItem aguardando = sessaoDoCardapio().withEssence("ZGY-BLUEBERRY");
        ComandaItem preparando = aguardando.withSessionPaid(t);

        assertThat(aguardando.essenceBurned()).isFalse();
        assertThat(preparando.sessionStatus()).isEqualTo(SessionStatus.PREPARANDO);
        assertThat(preparando.essenceBurned()).isTrue();
        assertThat(preparando.withSessionStatus(SessionStatus.ENTREGUE, t).essenceBurned()).isTrue();
        assertThat(ComandaItem.fromCatalog("CARVAO-1KG", BigDecimal.ONE, PRICED, "Carvão").essenceBurned()).isFalse();
    }
}
