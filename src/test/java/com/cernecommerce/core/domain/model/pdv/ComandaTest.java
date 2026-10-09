package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComandaTest {

    private static ComandaItem item(String sku, String unitPrice) {
        return ComandaItem.of(null, sku, BigDecimal.ONE, new BigDecimal(unitPrice), null, null, Instant.now());
    }

    private static Comanda open() {
        return Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1");
    }

    /** Linha de sessão com id fixo, para poder pendurar outras nela. */
    private static ComandaItem linha(Long id, String sku, String unitPrice, ConsumptionMode mode,
            boolean courtesy, Long linkedItemId) {
        return ComandaItem.of(id, sku, BigDecimal.ONE, new BigDecimal(unitPrice), new BigDecimal("5.00"),
                sku, Instant.now(), mode, courtesy, linkedItemId);
    }

    /** Open rosh #1 com duas trocas penduradas (#2, #3) e uma linha comum solta (#4). */
    private static Comanda comOpenRoshEDuasTrocas() {
        return Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", null, ComandaStatus.ABERTA, List.of(
                linha(1L, "SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null),
                linha(2L, "ESS-UVA", "0.00", ConsumptionMode.TROCA, true, 1L),
                linha(3L, "ESS-MENTA", "0.00", ConsumptionMode.TROCA, true, 1L),
                linha(4L, "BEB-COLA", "12.00", ConsumptionMode.NORMAL, false, null)),
                null, "caixa1", Instant.now(), null);
    }

    // ── Remoção de item (PDV-F012) ───────────────────────────────────────────────────────────

    @Test
    void withRemovedItem_dropsTheLineAndTheTrocasHangingOnIt() {
        Comanda result = comOpenRoshEDuasTrocas().withRemovedItem(1L);

        // Só a linha comum sobra — a sessão e as duas trocas dela saíram juntas.
        assertThat(result.items()).extracting(ComandaItem::id).containsExactly(4L);
        assertThat(result.runningTotal()).isEqualByComparingTo("12.00");
    }

    /** A troca não existe sem a sessão: deixá-la para trás apontaria para um id que sumiu. */
    @Test
    void itemsRemovedWith_listsTheLineAndItsTrocas() {
        assertThat(comOpenRoshEDuasTrocas().itemsRemovedWith(1L))
                .extracting(ComandaItem::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    void withRemovedItem_removingALeafLineTouchesNothingElse() {
        Comanda result = comOpenRoshEDuasTrocas().withRemovedItem(4L);

        assertThat(result.items()).extracting(ComandaItem::id).containsExactly(1L, 2L, 3L);
    }

    /** Remover só a troca é legítimo — ela é a folha, não arrasta ninguém. */
    @Test
    void withRemovedItem_removingOnlyOneTrocaKeepsTheSessionAndTheOther() {
        Comanda result = comOpenRoshEDuasTrocas().withRemovedItem(2L);

        assertThat(result.items()).extracting(ComandaItem::id).containsExactly(1L, 3L, 4L);
    }

    /**
     * {@code SABOR_EXTRA} é linha própria e pode estar cobrada. O domínio o expõe para o service
     * barrar a remoção — arrastá-lo tiraria valor da conta sem ninguém pedir.
     */
    @Test
    void chargedChildrenOf_findsSaborExtraButNotTroca() {
        Comanda comanda = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", null, ComandaStatus.ABERTA, List.of(
                linha(1L, "SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null),
                linha(2L, "ESS-UVA", "0.00", ConsumptionMode.TROCA, true, 1L),
                linha(3L, "SESS-MANGA", "35.00", ConsumptionMode.SABOR_EXTRA, false, 1L)),
                null, "caixa1", Instant.now(), null);

        assertThat(comanda.chargedChildrenOf(1L)).extracting(ComandaItem::id).containsExactly(3L);
        assertThat(comanda.chargedChildrenOf(2L)).isEmpty();
    }

    @Test
    void withRemovedItem_refusesAnItemThatIsNotInThisComanda() {
        assertThatThrownBy(() -> comOpenRoshEDuasTrocas().withRemovedItem(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pertence");
    }

    @Test
    void withRemovedItem_refusesNullItemId() {
        assertThatThrownBy(() -> comOpenRoshEDuasTrocas().withRemovedItem(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Rede de segurança do domínio: linha de mesa fechada é histórico de um pedido já pago. */
    @Test
    void withRemovedItem_refusesOnAComandaThatIsNotOpen() {
        Comanda fechada = comOpenRoshEDuasTrocas().closed(500L, Instant.now());

        assertThatThrownBy(() -> fechada.withRemovedItem(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Remover a última linha deixa a comanda vazia, que é estado legítimo — é como ela nasce. */
    @Test
    void withRemovedItem_canEmptyTheComanda() {
        Comanda umaLinha = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", null, ComandaStatus.ABERTA,
                List.of(linha(1L, "BEB-COLA", "12.00", ConsumptionMode.NORMAL, false, null)),
                null, "caixa1", Instant.now(), null);

        assertThat(umaLinha.withRemovedItem(1L).items()).isEmpty();
    }

    // ── Abertura ─────────────────────────────────────────────────────────────────────────────

    @Test
    void open_startsAbertaAndEmpty() {
        Comanda comanda = open();

        assertThat(comanda.id()).isNull();
        assertThat(comanda.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(comanda.isOpen()).isTrue();
        assertThat(comanda.items()).isEmpty();
        assertThat(comanda.orderId()).isNull();
        assertThat(comanda.closedAt()).isNull();
        assertThat(comanda.runningTotal()).isEqualByComparingTo("0");
    }

    @Test
    void open_rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> Comanda.open(null, "LOJA-01", "Mesa 4", "caixa1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sessionId");
        assertThatThrownBy(() -> Comanda.open(1L, " ", "Mesa 4", "caixa1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("warehouseCode");
        assertThatThrownBy(() -> Comanda.open(1L, "LOJA-01", " ", "caixa1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tableOrCustomerLabel");
        assertThatThrownBy(() -> Comanda.open(1L, "LOJA-01", "Mesa 4", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("openedBy");
    }

    // ── Acúmulo de itens ─────────────────────────────────────────────────────────────────────

    @Test
    void withAddedItem_accumulatesWithoutMutatingTheOriginal() {
        Comanda comanda = open();
        Comanda withOneItem = comanda.withAddedItem(item("ESS-MENTA", "25.00"));

        assertThat(comanda.items()).isEmpty();
        assertThat(withOneItem.items()).hasSize(1);
        assertThat(withOneItem.runningTotal()).isEqualByComparingTo("25.00");

        Comanda withTwoItems = withOneItem.withAddedItem(item("CARV-001", "15.00"));
        assertThat(withTwoItems.items()).hasSize(2);
        assertThat(withTwoItems.runningTotal()).isEqualByComparingTo("40.00");
    }

    @Test
    void withAddedItem_refusesOnNonAbertaComanda() {
        Comanda closed = open().withAddedItem(item("ESS-MENTA", "25.00")).closed(99L, Instant.now());

        assertThatThrownBy(() -> closed.withAddedItem(item("CARV-001", "15.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Fechamento ───────────────────────────────────────────────────────────────────────────

    @Test
    void closed_stampsOrderIdAndClosedAt() {
        Comanda comanda = open().withAddedItem(item("ESS-MENTA", "25.00"));
        Comanda fechada = comanda.closed(42L, Instant.now());

        assertThat(fechada.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(fechada.orderId()).isEqualTo(42L);
        assertThat(fechada.closedAt()).isNotNull();
        assertThat(fechada.items()).hasSize(1);
    }

    @Test
    void closed_refusesToCloseTwice() {
        Comanda fechada = open().withAddedItem(item("ESS-MENTA", "25.00")).closed(42L, Instant.now());

        assertThatThrownBy(() -> fechada.closed(43L, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closed_requiresOrderId() {
        Comanda comanda = open().withAddedItem(item("ESS-MENTA", "25.00"));

        assertThatThrownBy(() -> comanda.closed(null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("orderId");
    }

    // ── Cancelamento ─────────────────────────────────────────────────────────────────────────

    @Test
    void cancelled_hasClosedAtButNoOrderId() {
        Comanda comanda = open().withAddedItem(item("ESS-MENTA", "25.00"));
        Comanda cancelada = comanda.cancelled(Instant.now());

        assertThat(cancelada.status()).isEqualTo(ComandaStatus.CANCELADA);
        assertThat(cancelada.orderId()).isNull();
        assertThat(cancelada.closedAt()).isNotNull();
        // Itens permanecem no registro — é o rastro de que a comanda existiu, mesmo abandonada.
        assertThat(cancelada.items()).hasSize(1);
    }

    @Test
    void cancelled_refusesOnAlreadyClosedComanda() {
        Comanda fechada = open().withAddedItem(item("ESS-MENTA", "25.00")).closed(42L, Instant.now());

        assertThatThrownBy(() -> fechada.cancelled(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Invariantes de reconstituição (espelham o CHECK da V104) ────────────────────────────

    @Test
    void of_rejectsAbertaComandaWithClosedAtOrOrderId() {
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA,
                List.of(), 42L, "caixa1", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsFechadaComandaWithoutOrderIdOrClosedAt() {
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(), null, "caixa1", Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(), 42L, "caixa1", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_rejectsCanceladaComandaWithOrderIdOrWithoutClosedAt() {
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA,
                List.of(), 42L, "caixa1", Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA,
                List.of(), null, "caixa1", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant openedAt = Instant.parse("2026-08-18T18:00:00Z");
        Instant closedAt = Instant.parse("2026-08-18T20:00:00Z");
        Comanda comanda = Comanda.of(7L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(item("ESS-MENTA", "25.00")), 99L, "caixa1", openedAt, closedAt);

        assertThat(comanda.id()).isEqualTo(7L);
        assertThat(comanda.orderId()).isEqualTo(99L);
        assertThat(comanda.openedAt()).isEqualTo(openedAt);
        assertThat(comanda.closedAt()).isEqualTo(closedAt);
    }

    @Test
    void itemsAreDefensivelyCopied() {
        List<ComandaItem> items = new java.util.ArrayList<>(List.of(item("ESS-MENTA", "25.00")));
        Comanda comanda = Comanda.of(1L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA, items, null,
                "caixa1", Instant.now(), null);

        items.add(item("CARV-001", "15.00"));

        assertThat(comanda.items()).hasSize(1);
    }

    // ── Conta dividida (PDV-F017) e troca de mesa (PDV-F016) ─────────────────────────────────

    private static Comanda comandaComLinhas(ComandaItem... itens) {
        Comanda c = open();
        for (ComandaItem i : itens) {
            c = c.withAddedItem(i);
        }
        return Comanda.of(10L, c.sessionId(), c.warehouseCode(), c.tableOrCustomerLabel(),
                c.customerId(), c.status(), c.items(), c.orderId(), c.openedBy(), c.openedAt(), c.closedAt());
    }

    /**
     * O número que a tela mostra como "falta pagar" — não o total consumido. Antes de PDV-F017 as
     * duas leituras coincidiam porque a conta só podia ser fechada inteira.
     */
    @Test
    void runningTotal_countsOnlyTheLinesStillOpen() {
        Comanda comanda = comandaComLinhas(
                linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "ESS-B", "20.00", ConsumptionMode.NORMAL, false, null));

        assertThat(comanda.runningTotal()).isEqualByComparingTo("50.00");

        Comanda parcial = comanda.withItemsClosedIn(99L, List.of(1L));

        assertThat(parcial.runningTotal()).isEqualByComparingTo("20.00");
        assertThat(parcial.openItems()).hasSize(1);
        assertThat(parcial.items()).hasSize(2);
    }

    /**
     * PDV-C042 — cortesia em aberto não é dívida. O rosh grátis do dia de duplo, lançado depois de a
     * sessão ser paga, fica sem pedido; contá-lo como "a cobrar" deixava a mesa sem caminho de saída.
     */
    @Test
    void owedItems_ignoresOpenCourtesyLines() {
        Comanda comanda = comandaComLinhas(
                linha(1L, "SESS-1", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "SESS-1", "0.00", ConsumptionMode.NORMAL, true, null))
                .withItemsClosedIn(99L, List.of(1L));

        assertThat(comanda.openItems()).extracting(ComandaItem::id).containsExactly(2L);
        assertThat(comanda.owedItems()).isEmpty();
        assertThat(comanda.hasNothingOwed()).isTrue();
    }

    /** Mesa sem nenhuma linha cobrada não "deve nada" só porque o resto é cortesia: não há pedido. */
    @Test
    void hasNothingOwed_requiresAChargedLine() {
        Comanda soCortesia = comandaComLinhas(linha(1L, "SESS-1", "0.00", ConsumptionMode.NORMAL, true, null));

        assertThat(soCortesia.owedItems()).isEmpty();
        assertThat(soCortesia.hasNothingOwed()).isFalse();
    }

    /** Fechar parte da conta NÃO muda o status: quem encerra a mesa é o service. */
    @Test
    void withItemsClosedIn_doesNotChangeTheStatus() {
        Comanda parcial = comandaComLinhas(
                linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "ESS-B", "20.00", ConsumptionMode.NORMAL, false, null))
                .withItemsClosedIn(99L, List.of(1L));

        assertThat(parcial.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(parcial.orderId()).isNull();
        assertThat(parcial.isFullyCharged()).isFalse();
    }

    @Test
    void isFullyCharged_onlyWhenNoLineIsOpen() {
        Comanda comanda = comandaComLinhas(
                linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "ESS-B", "20.00", ConsumptionMode.NORMAL, false, null));

        assertThat(comanda.withItemsClosedIn(99L, List.of(1L)).isFullyCharged()).isFalse();
        assertThat(comanda.withItemsClosedIn(99L, List.of(1L, 2L)).isFullyCharged()).isTrue();
        assertThat(comanda.runningTotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void withItemsClosedIn_rejectsALineThatIsNotOpenHere() {
        Comanda comanda = comandaComLinhas(linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null));

        assertThatThrownBy(() -> comanda.withItemsClosedIn(99L, List.of(42L)))
                .isInstanceOf(IllegalArgumentException.class);
        // E cobrar duas vezes a mesma linha também não passa.
        Comanda cobrada = comanda.withItemsClosedIn(99L, List.of(1L));
        assertThatThrownBy(() -> cobrada.withItemsClosedIn(100L, List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** PDV-F016 — trocar de mesa é só o rótulo; o consumo não se move. */
    @Test
    void withLabel_changesOnlyTheLabel() {
        Comanda comanda = comandaComLinhas(linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null));

        Comanda renomeada = comanda.withLabel("Mesa 7");

        assertThat(renomeada.tableOrCustomerLabel()).isEqualTo("Mesa 7");
        assertThat(renomeada.items()).hasSize(1);
        assertThat(renomeada.warehouseCode()).isEqualTo(comanda.warehouseCode());
        assertThat(renomeada.sessionId()).isEqualTo(comanda.sessionId());
        assertThat(renomeada.runningTotal()).isEqualByComparingTo("30.00");
    }

    @Test
    void withLabel_rejectsBlankAndClosedComanda() {
        Comanda comanda = comandaComLinhas(linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null));

        assertThatThrownBy(() -> comanda.withLabel("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> comanda.cancelled(Instant.now()).withLabel("Mesa 7"))
                .isInstanceOf(IllegalStateException.class);
    }
}
