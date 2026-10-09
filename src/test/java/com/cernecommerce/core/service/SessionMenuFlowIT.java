package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ComandaHasOpenItemsException;
import com.cernecommerce.core.domain.exception.pdv.SessionAssetUnavailableException;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDV-F021 de ponta a ponta contra banco real: cardápio configurado → sessão lançada com vaso
 * grande → utensílios presos → 2º rosh → fechamento → utensílios livres de novo. É o teste que
 * exercita as consultas de disponibilidade e a trava dos tipos de utensílio, que os testes de
 * unidade mockam.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SessionMenuFlowIT {

    @Autowired SessionMenuUseCase sessionMenuUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static int disponivel(SessionMenu menu, String codigo) {
        return menu.utensilios().stream().filter(a -> a.tipo().codigo().equals(codigo)).findFirst()
                .orElseThrow().disponivel();
    }

    @Test
    void sessionWithBigVase_holdsTheUtensilsUntilTheTableCloses() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String vasoP = "VP" + suffix;
        String vasoG = "VG" + suffix;
        String pinca = "PI" + suffix;
        String operator = "caixa-" + suffix;
        String warehouse = "LOUNGE-" + suffix;

        // Cardápio: vaso grande só 1, pinça 2 (inclusa).
        sessionMenuUseCase.createAssetType(vasoP, "Vaso pequeno " + suffix, 2, false);
        sessionMenuUseCase.createAssetType(vasoG, "Vaso grande " + suffix, 1, false);
        SessionAssetType pincaType = sessionMenuUseCase.createAssetType(pinca, "Pinça " + suffix, 2, true);
        sessionMenuUseCase.updateSettings(new SessionSettings(vasoP, vasoG, new BigDecimal("10.00"), Set.of()));
        SessionTier premium = sessionMenuUseCase.createTier("Premium " + suffix, new BigDecimal("30.00"),
                "Luk, Smynar, Nay", 2);
        SessionTier tradicional = sessionMenuUseCase.createTier("Tradicional " + suffix, new BigDecimal("25.00"),
                "Zgy, Zomo, Pred", 1);

        estoqueUseCase.createWarehouse(warehouse, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);
        Comanda mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 4", operator);
        flushAndClear();

        // 1. Sessão Premium com vaso grande: R$ 30 + R$ 10. PDV-F027: nasce aguardando pagamento.
        Comanda comSessao = comandaUseCase.addSession(mesa.id(), premium.id(), "Luk Uva", true, operator);
        flushAndClear();
        ComandaItem sessao = comSessao.items().get(0);
        assertThat(sessao.mode()).isEqualTo(ConsumptionMode.SESSAO);
        assertThat(sessao.unitPrice()).isEqualByComparingTo("40.00");
        assertThat(sessao.notes()).isEqualTo("Luk Uva · Vaso grande");
        assertThat(sessao.sessionStatus()).isEqualTo(SessionStatus.AGUARDANDO_PAGAMENTO);
        assertThat(comandaUseCase.getComanda(mesa.id()).items().get(0).setup().vasoGrande()).isTrue();

        SessionMenu emUso = sessionMenuUseCase.getMenu();
        assertThat(disponivel(emUso, vasoG)).isZero();
        assertThat(disponivel(emUso, pinca)).isEqualTo(1);
        assertThat(disponivel(emUso, vasoP)).isEqualTo(2);

        // 2. Segunda sessão com vaso grande: o único está na mesa — recusada.
        Comanda outraMesa = comandaUseCase.openComanda(caixa.id(), "Mesa 5", operator);
        assertThatThrownBy(() -> comandaUseCase.addSession(outraMesa.id(), premium.id(), "Nay Menta", true, operator))
                .isInstanceOf(SessionAssetUnavailableException.class);

        // 3. 2º rosh (fora de dia de promoção): cobra a faixa escolhida, sem utensílio novo.
        Comanda comRosh = comandaUseCase.addRoshExtra(mesa.id(), sessao.id(), tradicional.id(), "Pred Menta",
                operator);
        flushAndClear();
        ComandaItem rosh = comRosh.items().stream().filter(i -> i.mode() == ConsumptionMode.ROSH_EXTRA)
                .findFirst().orElseThrow();
        assertThat(rosh.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(rosh.linkedItemId()).isEqualTo(sessao.id());
        assertThat(disponivel(sessionMenuUseCase.getMenu(), pinca)).isEqualTo(1);

        // 4. Pagar a sessão (fechamento parcial de R$ 65) a leva ao preparo, sem encerrar a mesa nem
        //    devolver utensílio.
        Order order = comandaUseCase.closeComanda(mesa.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("65.00"), null)),
                null, false, List.of(sessao.id(), rosh.id()), operator);
        flushAndClear();

        assertThat(order.netAmount()).isEqualByComparingTo("65.00");
        assertThat(order.items()).extracting(i -> i.mode())
                .containsExactlyInAnyOrder(ConsumptionMode.SESSAO, ConsumptionMode.ROSH_EXTRA);
        Comanda paga = comandaUseCase.getComanda(mesa.id());
        assertThat(paga.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(paga.items()).filteredOn(i -> i.id().equals(sessao.id())).singleElement()
                .satisfies(i -> assertThat(i.sessionStatus()).isEqualTo(SessionStatus.PREPARANDO));
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoG)).isZero();

        // 5. PDV-F027 — outra sessão em paralelo, no vaso pequeno. A mesa segue sem encerrar.
        Comanda comDuas = comandaUseCase.addSession(mesa.id(), premium.id(), "Nay", false, operator);
        flushAndClear();
        ComandaItem paralela = comDuas.items().stream()
                .filter(i -> i.mode() == ConsumptionMode.SESSAO && !i.id().equals(sessao.id()))
                .findFirst().orElseThrow();
        assertThat(paralela.sessionStatus()).isEqualTo(SessionStatus.AGUARDANDO_PAGAMENTO);
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoP)).isEqualTo(1);
        assertThat(disponivel(sessionMenuUseCase.getMenu(), pinca)).isZero();
        assertThatThrownBy(() -> comandaUseCase.finishComanda(mesa.id(), operator))
                .isInstanceOf(ComandaHasOpenItemsException.class);

        // 6. Entregue e recolhida: o rosh da MESMA sessão é promovido; a paralela não é tocada.
        comandaUseCase.updateSessionStatus(mesa.id(), sessao.id(), SessionStatus.ENTREGUE, operator);
        Comanda aposRecolher = comandaUseCase.updateSessionStatus(mesa.id(), sessao.id(), SessionStatus.RECOLHIDO,
                operator);
        flushAndClear();
        assertThat(aposRecolher.items()).filteredOn(i -> i.id().equals(rosh.id()))
                .singleElement().satisfies(i -> assertThat(i.sessionStatus()).isEqualTo(SessionStatus.PREPARANDO));
        assertThat(aposRecolher.items()).filteredOn(i -> i.id().equals(paralela.id()))
                .singleElement().satisfies(i -> assertThat(i.sessionStatus())
                        .isEqualTo(SessionStatus.AGUARDANDO_PAGAMENTO));
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoG)).isZero();

        // 7. O rosh recolhido fecha o grupo: utensílios de volta.
        comandaUseCase.updateSessionStatus(mesa.id(), rosh.id(), SessionStatus.RECOLHIDO, operator);
        flushAndClear();
        SessionMenu livre = sessionMenuUseCase.getMenu();
        assertThat(disponivel(livre, vasoG)).isEqualTo(1);
        assertThat(disponivel(livre, pinca)).isEqualTo(1);

        // 8. Repetir a sessão recolhida com sabor novo: mesma faixa e vaso grande, utensílio de novo.
        Comanda comRepetida = comandaUseCase.repeatSession(mesa.id(), sessao.id(),
                new ComandaUseCase.RepeatSessionCommand("Luk Menta", false, null, null), operator);
        flushAndClear();
        ComandaItem repetida = comRepetida.items().stream()
                .filter(i -> i.mode() == ConsumptionMode.SESSAO && i.sessionStatus() == SessionStatus.AGUARDANDO_PAGAMENTO
                        && !i.id().equals(paralela.id()))
                .findFirst().orElseThrow();
        assertThat(repetida.unitPrice()).isEqualByComparingTo("40.00");
        assertThat(repetida.notes()).isEqualTo("Luk Menta · Vaso grande");
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoG)).isZero();

        // 9. Paga as duas, recolhe as duas, e a mesa encerra com o último pedido.
        Order segundo = comandaUseCase.closeComanda(mesa.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("70.00"), null)),
                null, false, List.of(paralela.id(), repetida.id()), operator);
        flushAndClear();
        comandaUseCase.updateSessionStatus(mesa.id(), paralela.id(), SessionStatus.RECOLHIDO, operator);
        comandaUseCase.updateSessionStatus(mesa.id(), repetida.id(), SessionStatus.RECOLHIDO, operator);
        flushAndClear();
        SessionMenu tudoLivre = sessionMenuUseCase.getMenu();
        assertThat(disponivel(tudoLivre, vasoG)).isEqualTo(1);
        assertThat(disponivel(tudoLivre, vasoP)).isEqualTo(2);
        assertThat(disponivel(tudoLivre, pinca)).isEqualTo(pincaType.quantidadeTotal());

        Comanda encerrada = comandaUseCase.finishComanda(mesa.id(), operator);
        assertThat(encerrada.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(encerrada.orderId()).isEqualTo(segundo.id());
    }

    @Test
    void cancellingTheTable_releasesTheUtensils() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String vasoP = "VP" + suffix;
        String operator = "caixa-" + suffix;
        String warehouse = "LOUNGE-" + suffix;

        sessionMenuUseCase.createAssetType(vasoP, "Vaso " + suffix, 1, false);
        sessionMenuUseCase.updateSettings(new SessionSettings(vasoP, null, BigDecimal.ZERO, Set.of()));
        SessionTier tier = sessionMenuUseCase.createTier("Sence " + suffix, new BigDecimal("40.00"), "Sence", 3);
        estoqueUseCase.createWarehouse(warehouse, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);
        Comanda mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 9", operator);

        comandaUseCase.addSession(mesa.id(), tier.id(), "Sence Blue", false, operator);
        flushAndClear();
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoP)).isZero();

        comandaUseCase.cancelComanda(mesa.id(), operator);
        flushAndClear();
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoP)).isEqualTo(1);
    }

    /**
     * PDV-F042 — a sessão do cardápio com o sabor do catálogo consome USO da lata aberta, sem tirar
     * unidade da prateleira enquanto a lata rende; remover a sessão antes do preparo devolve o uso.
     */
    @Test
    void sessionWithCatalogEssence_consumesTheOpenPackage_andRemovalGivesTheUseBack() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String operator = "caixa-" + suffix;
        String warehouse = "LOUNGE-" + suffix;
        String sabor = "ZOMO-" + suffix;

        sessionMenuUseCase.createAssetType("VP" + suffix, "Vaso pequeno " + suffix, 2, false);
        sessionMenuUseCase.updateSettings(new SessionSettings("VP" + suffix, null, new BigDecimal("10.00"), Set.of()));
        SessionTier tradicional = sessionMenuUseCase.createTier("Tradicional " + suffix, new BigDecimal("25.00"),
                "Zomo", 1);

        estoqueUseCase.createWarehouse(warehouse, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sabor, "Zomo Blueberry " + suffix, "Essências", List.of(),
                com.cernecommerce.core.domain.model.estoque.Pricing.of(new BigDecimal("40.00"), null,
                        new BigDecimal("70.00")));
        estoqueUseCase.updateProduct(sabor, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 5, null));
        estoqueUseCase.adjustStock(sabor, warehouse,
                com.cernecommerce.core.domain.model.estoque.MovementType.ENTRADA, new BigDecimal("3.000"),
                "carga inicial", operator);
        // Inventário inicial (EST-F033): a lata da bancada já tinha rendido 3 de 5.
        estoqueUseCase.registerOpenPackage(sabor, warehouse, 2, operator);

        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);
        Comanda mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 9", operator);
        flushAndClear();

        ComandaItem sessao = comandaUseCase.addSession(mesa.id(), new ComandaUseCase.AddSessionCommand(
                tradicional.id(), null, false, null, List.of(), false, null, null, false, sabor, null),
                operator).items().get(0);
        flushAndClear();

        assertThat(sessao.essenceSku()).isEqualTo(sabor);
        assertThat(sessao.notes()).isEqualTo("Zomo Blueberry " + suffix);
        assertThat(sessao.packageUses()).isEqualTo(4);
        assertThat(comandaUseCase.getComanda(mesa.id()).items().get(0).essenceSku()).isEqualTo(sabor);
        assertThat(estoqueUseCase.findOpenPackage(sabor, warehouse).uses()).isEqualTo(4);
        // A lata já estava aberta: nenhuma unidade saiu da prateleira.
        assertThat(estoqueUseCase.getStockBalance(sabor, warehouse).quantity()).isEqualByComparingTo("3.000");

        comandaUseCase.removeItem(mesa.id(), sessao.id(), operator);
        flushAndClear();

        assertThat(estoqueUseCase.findOpenPackage(sabor, warehouse).uses()).isEqualTo(3);
        assertThat(estoqueUseCase.getStockBalance(sabor, warehouse).quantity()).isEqualByComparingTo("3.000");
    }
}
