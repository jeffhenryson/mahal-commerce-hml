package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.NoPayLaterSessionException;
import com.cernecommerce.core.domain.exception.pdv.NotASessionLineException;
import com.cernecommerce.core.domain.exception.pdv.SessionNotAwaitingPaymentException;
import com.cernecommerce.core.domain.exception.pdv.SessionNotEditableException;
import com.cernecommerce.core.domain.exception.pdv.SessionPaidPriceChangeException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionProgress;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionSetup;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PDV-F057 a PDV-F060 — jumbo de cortesia, edição da sessão, pagar no final depois de lançada e a
 * compra na loja na conta da mesa.
 */
@ExtendWith(MockitoExtension.class)
class ComandaSessaoEdicaoServiceTest {

    @Mock ComandaRepository comandaRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock PdvService pdvService;
    @Mock NotificationUseCase notificationUseCase;
    @Mock UserRepository userRepository;
    @Mock KitBuilderUseCase kitBuilderUseCase;
    @Mock SessionMenuService sessionMenu;

    ComandaService comandaService;

    private static final SessionTier PREMIUM = new SessionTier(2L, "Premium", new BigDecimal("30.00"),
            "Luk, Smynar, Nay", 2, true);
    /** Vaso grande +R$ 10, carvão jumbo +R$ 8. */
    private static final SessionSettings SETTINGS = new SessionSettings("VASO_P", "VASO_G",
            new BigDecimal("10.00"), Set.of(), new BigDecimal("8.00"));
    private static final List<SessionAssetType> KIT_PADRAO = List.of(
            new SessionAssetType(1L, "VASO_P", "Vaso pequeno", 5, false, true),
            new SessionAssetType(3L, "PINCA", "Pinça", 5, true, true));
    private static final List<SessionAssetType> KIT_GRANDE = List.of(
            new SessionAssetType(2L, "VASO_G", "Vaso grande", 2, false, true),
            new SessionAssetType(3L, "PINCA", "Pinça", 5, true, true));
    private static final SessionAddon FILTRO = new SessionAddon(1L, "Filtro de gelo", new BigDecimal("5.00"), 1, true);
    private static final Pricing PRECO_ESSENCIA = Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00"));

    @BeforeEach
    void setUp() {
        comandaService = new ComandaService(comandaRepository, estoqueUseCase, orderRepository,
                orderPaymentRepository, cashbackUseCase, pdvService, notificationUseCase, userRepository,
                BigDecimal.TEN, kitBuilderUseCase, sessionMenu, false);
    }

    private static Comanda comanda(ComandaItem... items) {
        return Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", null, ComandaStatus.ABERTA, List.of(items), null,
                "caixa1", Instant.now(), null);
    }

    private static ComandaItem sessaoEm(Long id, SessionProgress progress, Long closedIn, SessionSetup setup) {
        return ComandaItem.of(id, PREMIUM.sku(), BigDecimal.ONE, PREMIUM.preco(), null, "Sessão Premium",
                Instant.now(), ConsumptionMode.SESSAO, false, null, "Zomo Blueberry", null, closedIn,
                null, null, null, null, null, progress, setup);
    }

    private static ComandaItem roshNaFila(Long id, Long parentId) {
        return ComandaItem.of(id, PREMIUM.sku(), BigDecimal.ONE, BigDecimal.ZERO, null, "2º rosh Premium (duplo rosh)",
                Instant.now().plusSeconds(1), ConsumptionMode.ROSH_EXTRA, true, parentId, null, null, null,
                null, null, null, null, null, SessionProgress.queued(false));
    }

    private static ComandaItem compraNaLoja(Long id, String price, String discount) {
        return ComandaItem.of(id, "ISQ-01", BigDecimal.ONE, new BigDecimal(price), new BigDecimal("4.00"),
                "Isqueiro", Instant.now()).asStorePurchase(discount == null ? null : new BigDecimal(discount));
    }

    private static ComandaItem agua(Long id, String price) {
        return ComandaItem.of(id, "AGUA", BigDecimal.ONE, new BigDecimal(price), new BigDecimal("1.00"), "Água",
                Instant.now());
    }

    private static SessionProgress entregue() {
        Instant t = Instant.now();
        return new SessionProgress(SessionStatus.ENTREGUE, t, t, null);
    }

    private static SessionProgress recolhido() {
        Instant t = Instant.now();
        return new SessionProgress(SessionStatus.RECOLHIDO, t, t, t);
    }

    private static EstoqueUseCase.CatalogSaleInfo sabor(String nome) {
        return new EstoqueUseCase.CatalogSaleInfo(nome, PRECO_ESSENCIA, true, true, null, 5, false, false);
    }

    private static ComandaUseCase.AddSessionCommand sessao(Charcoal carvao, boolean carvaoCortesia) {
        return new ComandaUseCase.AddSessionCommand(2L, "Zomo", false, carvao, List.of(), false, null, null, false,
                null, null, null, null, null, carvaoCortesia);
    }

    private static ComandaUseCase.EditSessionCommand edicao(String essencia, String essenciaSku, boolean vasoGrande,
            Charcoal carvao, boolean carvaoCortesia, List<Long> adicionalIds) {
        return new ComandaUseCase.EditSessionCommand(2L, essencia, essenciaSku, null, null, vasoGrande, carvao,
                carvaoCortesia, adicionalIds);
    }

    /** O save falso dá id às linhas novas, como o banco faria. */
    private void givenSaveAssignsItemIds() {
        when(comandaRepository.save(any())).thenAnswer(inv -> {
            Comanda c = inv.getArgument(0);
            List<ComandaItem> items = new ArrayList<>();
            long next = 100;
            for (ComandaItem i : c.items()) {
                items.add(i.id() != null ? i : ComandaItem.of(next++, i.sku(), i.quantity(), i.unitPrice(),
                        i.costPrice(), i.productName(), i.addedAt(), i.mode(), i.courtesy(), i.linkedItemId(),
                        i.notes(), i.surchargeAmount(), i.closedInOrderId(), i.packageUses(),
                        i.packageSessionsPerUnit(), null, null, null, i.session(), i.setup(), i.essenceSku(),
                        i.essenceSku2(), i.essence2Package(), i.essenceRatio(), i.storePurchase(),
                        i.storeDiscountAmount()));
            }
            return Comanda.of(c.id(), c.sessionId(), c.warehouseCode(), c.tableOrCustomerLabel(), c.customerId(),
                    c.status(), items, c.orderId(), c.openedBy(), c.openedAt(), c.closedAt());
        });
    }

    private void givenSaveReturnsTheComanda() {
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenMenuForNewSession() {
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.reserveAssetsForSession(SETTINGS, false)).thenReturn(KIT_PADRAO);
    }

    private void givenClosingSucceeds() {
        when(pdvService.getCurrentSession("caixa1")).thenReturn(CashRegisterSession.of(1L, "caixa1",
                Instant.now(), BigDecimal.TEN, "LOJA-01", null, null, null, null, null,
                CashRegisterSession.Status.OPEN));
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return Order.of(500L, o.orderNumber(), o.channel(), o.status(), o.customerId(), o.sessionId(),
                    o.warehouseCode(), o.items(), o.grossAmount(), o.discountAmount(), o.cashbackRedeemed(),
                    o.netAmount(), o.changeAmount(), o.cancelReason(), o.createdAt(), o.paidAt(), o.concludedAt(),
                    o.cancelledAt(), o.refundedAt(), o.reservedAt(), o.separatedAt(), o.shippedAt(),
                    o.deliveredAt(), o.version(), o.comandaId(), o.tableLabel(), o.serviceFeeAmount());
        });
        givenSaveReturnsTheComanda();
    }

    private static List<PaymentCommand> dinheiro(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    // ── PDV-F057 — carvão jumbo de cortesia ──────────────────────────────────────────────────

    @Test
    void addSession_jumboDeCortesia_ficaSemOAcrescimo() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenMenuForNewSession();
        givenSaveAssignsItemIds();

        ComandaItem linha = comandaService.addSession(10L, sessao(Charcoal.JUMBO, true), "caixa1").items().get(0);

        assertThat(linha.unitPrice()).isEqualByComparingTo("30.00");
        assertThat(linha.setup().charcoal()).isEqualTo(Charcoal.JUMBO);
        assertThat(linha.setup().charcoalCourtesy()).isTrue();
        assertThat(linha.productName()).isEqualTo("Sessão Premium + carvão jumbo (cortesia)");
    }

    @Test
    void addSession_jumboCobrado_somaOAcrescimo() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenMenuForNewSession();
        givenSaveAssignsItemIds();

        ComandaItem linha = comandaService.addSession(10L, sessao(Charcoal.JUMBO, false), "caixa1").items().get(0);

        assertThat(linha.unitPrice()).isEqualByComparingTo("38.00");
        assertThat(linha.setup().charcoalCourtesy()).isFalse();
        assertThat(linha.productName()).isEqualTo("Sessão Premium + carvão jumbo");
    }

    @Test
    void addSession_cuboComCortesia_ignoraOCampoEGravaFalse() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenMenuForNewSession();
        givenSaveAssignsItemIds();

        ComandaItem linha = comandaService.addSession(10L, sessao(Charcoal.CUBO, true), "caixa1").items().get(0);

        assertThat(linha.unitPrice()).isEqualByComparingTo("30.00");
        assertThat(linha.setup().charcoal()).isEqualTo(Charcoal.CUBO);
        assertThat(linha.setup().charcoalCourtesy()).isFalse();
    }

    private ComandaItem origemComJumboDeCortesia() {
        return sessaoEm(1L, recolhido(), 400L, new SessionSetup(Charcoal.JUMBO, List.of(), false, true));
    }

    @Test
    void repeatSession_comCarvaoCortesia_repeteOJumboDeCortesia() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(origemComJumboDeCortesia())));
        givenMenuForNewSession();
        givenSaveAssignsItemIds();

        ComandaItem nova = comandaService.repeatSession(10L, 1L, new ComandaUseCase.RepeatSessionCommand(
                null, false, null, null, false, null, null, null, true), "caixa1").items().get(1);

        assertThat(nova.unitPrice()).isEqualByComparingTo("30.00");
        assertThat(nova.setup().charcoalCourtesy()).isTrue();
    }

    @Test
    void repeatSession_semOCampo_cobraOJumboMesmoComOrigemDeCortesia() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(origemComJumboDeCortesia())));
        givenMenuForNewSession();
        givenSaveAssignsItemIds();

        ComandaItem nova = comandaService.repeatSession(10L, 1L,
                new ComandaUseCase.RepeatSessionCommand(null, false, null, null), "caixa1").items().get(1);

        assertThat(nova.unitPrice()).isEqualByComparingTo("38.00");
        assertThat(nova.setup().charcoal()).isEqualTo(Charcoal.JUMBO);
        assertThat(nova.setup().charcoalCourtesy()).isFalse();
    }

    // ── PDV-F058 — editar a sessão ───────────────────────────────────────────────────────────

    @Test
    void editSession_trocaOSabor_devolveALataAntigaEConsomeANova() {
        ComandaItem antes = sessaoEm(1L, SessionProgress.awaitingPayment(), null, null)
                .withEssence("ZOMO-BLUE").withPackageCounter(3, 5);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(antes)));
        when(estoqueUseCase.resolveSaleInfo("PRED-MENTA")).thenReturn(sabor("Pred Menta"));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(estoqueUseCase.consumeSession("PRED-MENTA", "LOJA-01", BigDecimal.ONE, "caixa1"))
                .thenReturn(OpenPackage.open("PRED-MENTA", 1L, 5, "caixa1", Instant.now()).withUses(1));
        givenSaveReturnsTheComanda();

        ComandaUseCase.SessionEdited edited = comandaService.editSession(10L, 1L,
                edicao(null, "PRED-MENTA", false, null, false, List.of()), "caixa1");

        ComandaItem depois = edited.comanda().items().get(0);
        verify(estoqueUseCase).releaseSession("ZOMO-BLUE", "LOJA-01", BigDecimal.ONE);
        verify(estoqueUseCase).consumeSession("PRED-MENTA", "LOJA-01", BigDecimal.ONE, "caixa1");
        assertThat(depois.id()).isEqualTo(1L);
        assertThat(depois.essenceSku()).isEqualTo("PRED-MENTA");
        assertThat(depois.packageUses()).isEqualTo(1);
        assertThat(depois.notes()).isEqualTo("Pred Menta");
        assertThat(depois.sessionStatus()).isEqualTo(SessionStatus.AGUARDANDO_PAGAMENTO);
        assertThat(edited.before().essenceSku()).isEqualTo("ZOMO-BLUE");
        verify(sessionMenu, never()).release(any());
    }

    @Test
    void editSession_mesmoSku_naoMexeNoEstoque_eRecalculaAdicionaisECarvao() {
        ComandaItem antes = sessaoEm(1L, SessionProgress.awaitingPayment(), null, null)
                .withEssence("ZOMO-BLUE").withPackageCounter(3, 5);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(antes)));
        when(estoqueUseCase.resolveSaleInfo("ZOMO-BLUE")).thenReturn(sabor("Zomo Blueberry"));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.requireActiveAddons(List.of(1L))).thenReturn(List.of(FILTRO));
        givenSaveReturnsTheComanda();

        ComandaItem depois = comandaService.editSession(10L, 1L,
                edicao("Zomo Blueberry", "ZOMO-BLUE", false, Charcoal.JUMBO, false, List.of(1L)), "caixa1")
                .comanda().items().get(0);

        // 30 da faixa + 5 do filtro + 8 do jumbo.
        assertThat(depois.unitPrice()).isEqualByComparingTo("43.00");
        assertThat(depois.setup().addons()).singleElement().satisfies(a -> assertThat(a.addonId()).isEqualTo(1L));
        assertThat(depois.essenceSku()).isEqualTo("ZOMO-BLUE");
        assertThat(depois.packageUses()).isEqualTo(3);
        verify(estoqueUseCase, never()).consumeSession(any(), any(), any(), any());
        verify(estoqueUseCase, never()).releaseSession(any(), any(), any());
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void editSession_trocaOVaso_liberaEAlocaOsUtensilios() {
        when(comandaRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(comanda(sessaoEm(1L, SessionProgress.awaitingPayment(), null, null))));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.reserveAssetsForSession(SETTINGS, true)).thenReturn(KIT_GRANDE);
        givenSaveReturnsTheComanda();

        ComandaItem depois = comandaService.editSession(10L, 1L,
                edicao("Zomo Blueberry", null, true, null, false, List.of()), "caixa1").comanda().items().get(0);

        assertThat(depois.unitPrice()).isEqualByComparingTo("40.00");
        assertThat(depois.notes()).isEqualTo("Zomo Blueberry · Vaso grande");
        InOrder ordem = inOrder(sessionMenu);
        ordem.verify(sessionMenu).release(List.of(1L));
        ordem.verify(sessionMenu).reserveAssetsForSession(SETTINGS, true);
        ordem.verify(sessionMenu).allocate(1L, KIT_GRANDE);
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void editSession_pagaEmPreparo_semMudarOValor_passa() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessaoEm(1L, SessionProgress.preparing(Instant.now()), 400L, null))));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        givenSaveReturnsTheComanda();

        ComandaItem depois = comandaService.editSession(10L, 1L,
                edicao("Sence Menta", null, false, Charcoal.CUBO, false, List.of()), "caixa1").comanda().items().get(0);

        assertThat(depois.notes()).isEqualTo("Sence Menta");
        assertThat(depois.unitPrice()).isEqualByComparingTo("30.00");
        assertThat(depois.closedInOrderId()).isEqualTo(400L);
        assertThat(depois.sessionStatus()).isEqualTo(SessionStatus.PREPARANDO);
    }

    @Test
    void editSession_pagaMudandoOValor_eRecusadaAntesDeMexerEmNada() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessaoEm(1L, SessionProgress.preparing(Instant.now()), 400L, null))));
        when(sessionMenu.requireActiveTier(2L)).thenReturn(PREMIUM);
        when(sessionMenu.settings()).thenReturn(SETTINGS);

        assertThatThrownBy(() -> comandaService.editSession(10L, 1L,
                edicao("Zomo Blueberry", null, false, Charcoal.JUMBO, false, List.of()), "caixa1"))
                .isInstanceOf(SessionPaidPriceChangeException.class);
        verify(comandaRepository, never()).save(any());
        verify(sessionMenu, never()).release(any());
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void editSession_entregue_naoEEditavel() {
        when(comandaRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(comanda(sessaoEm(1L, entregue(), null, null))));

        assertThatThrownBy(() -> comandaService.editSession(10L, 1L,
                edicao("Zomo", null, false, null, false, List.of()), "caixa1"))
                .isInstanceOf(SessionNotEditableException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void editSession_recolhida_naoEEditavel() {
        when(comandaRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(comanda(sessaoEm(1L, recolhido(), 400L, null))));

        assertThatThrownBy(() -> comandaService.editSession(10L, 1L,
                edicao("Zomo", null, false, null, false, List.of()), "caixa1"))
                .isInstanceOf(SessionNotEditableException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void editSession_linhaQueNaoESessao_eRecusada() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(agua(1L, "5.00"))));

        assertThatThrownBy(() -> comandaService.editSession(10L, 1L,
                edicao("Zomo", null, false, null, false, List.of()), "caixa1"))
                .isInstanceOf(NotASessionLineException.class);
    }

    // ── PDV-F059 — passar para pagar no final ────────────────────────────────────────────────

    @Test
    void convertToPayLater_levaAoPreparo_eORoshLigadoHerdaAMarca() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessaoEm(1L, SessionProgress.awaitingPayment(), null, null), roshNaFila(2L, 1L))));
        givenSaveReturnsTheComanda();

        Comanda result = comandaService.convertSessionToPayLater(10L, 1L, "caixa1");

        ComandaItem sessao = result.items().get(0);
        ComandaItem rosh = result.items().get(1);
        assertThat(sessao.sessionStatus()).isEqualTo(SessionStatus.PREPARANDO);
        assertThat(sessao.isPayLater()).isTrue();
        assertThat(sessao.session().startedAt()).isNotNull();
        assertThat(rosh.isPayLater()).isTrue();
        assertThat(rosh.sessionStatus()).isEqualTo(SessionStatus.NA_FILA);
    }

    @Test
    void convertToPayLater_foraDeAguardandoPagamento_eRecusada() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessaoEm(1L, SessionProgress.preparing(Instant.now()), 400L, null))));

        assertThatThrownBy(() -> comandaService.convertSessionToPayLater(10L, 1L, "caixa1"))
                .isInstanceOf(SessionNotAwaitingPaymentException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── PDV-F060 — compra na loja na conta da mesa ───────────────────────────────────────────

    private static ComandaItem sessaoPagaNoFinal(Long id) {
        return sessaoEm(id, SessionProgress.preparingPayLater(Instant.now()), null, null);
    }

    @Test
    void addStorePurchaseToTab_criaAsLinhas_baixaOEstoque_eMarcaQueComprouNaLoja() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(sessaoPagaNoFinal(1L))));
        // Fora do catálogo da mesa (availableForTable=false): é compra de loja, não consumo do salão.
        when(estoqueUseCase.resolveSaleInfo("AGUA-500")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Água",
                Pricing.of(new BigDecimal("1.00"), null, new BigDecimal("5.00")), false, false, null, null, false,
                false));
        when(estoqueUseCase.resolveSaleInfo("ISQ-01")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Isqueiro",
                Pricing.of(new BigDecimal("4.00"), null, new BigDecimal("10.00")), false, false, null, null, false,
                false));
        givenSaveReturnsTheComanda();

        Comanda result = comandaService.addStorePurchaseToTab(10L, List.of(
                new SaleItemCommand("AGUA-500", new BigDecimal("2"), null, null),
                new SaleItemCommand("ISQ-01", BigDecimal.ONE, new BigDecimal("1.5"), "azul")), "caixa1");

        ComandaItem agua = result.items().get(1);
        ComandaItem isqueiro = result.items().get(2);
        assertThat(agua.storePurchase()).isTrue();
        assertThat(agua.mode()).isEqualTo(ConsumptionMode.NORMAL);
        assertThat(agua.netSubtotal()).isEqualByComparingTo("10.00");
        assertThat(isqueiro.storePurchase()).isTrue();
        assertThat(isqueiro.storeDiscountAmount()).isEqualByComparingTo("1.50");
        assertThat(isqueiro.notes()).isEqualTo("azul");
        assertThat(isqueiro.netSubtotal()).isEqualByComparingTo("8.50");
        assertThat(result.runningTotal()).isEqualByComparingTo("48.50");
        verify(pdvService).requireDiscountWithinLimit(any(Order.class));
        verify(estoqueUseCase).adjustStock(eq("AGUA-500"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(new BigDecimal("2")), anyString(), eq("caixa1"));
        verify(estoqueUseCase).adjustStock(eq("ISQ-01"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(BigDecimal.ONE), anyString(), eq("caixa1"));
        verify(comandaRepository).recordStorePurchase(eq(10L), eq(true), eq("caixa1"), any());
    }

    @Test
    void addStorePurchaseToTab_semSessaoPagaNoFinal_eRecusada() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessaoEm(1L, SessionProgress.preparing(Instant.now()), 400L, null))));

        assertThatThrownBy(() -> comandaService.addStorePurchaseToTab(10L,
                List.of(new SaleItemCommand("AGUA-500", BigDecimal.ONE, null, null)), "caixa1"))
                .isInstanceOf(NoPayLaterSessionException.class);
        verifyNoInteractions(estoqueUseCase);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void closeComanda_parcialComSessaoECompraNaLoja_geraUmPedidoComTudo_semTaxa() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessaoPagaNoFinal(1L), compraNaLoja(2L, "10.00", "1.50"))));
        givenClosingSucceeds();

        Order order = comandaService.closeComanda(10L, dinheiro("38.50"), null, true, List.of(1L, 2L), "caixa1");

        assertThat(order.items()).hasSize(2);
        assertThat(order.items().get(1).discountAmount()).isEqualByComparingTo("1.50");
        assertThat(order.netAmount()).isEqualByComparingTo("38.50");
        assertThat(order.serviceFeeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void closeComanda_total_aTaxaDeServicoIgnoraACompraNaLoja() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessaoEm(1L, recolhido(), null, null), agua(2L, "20.00"), compraNaLoja(3L, "10.00", null))));
        givenClosingSucceeds();

        Order order = comandaService.closeComanda(10L, dinheiro("62.00"), null, true, null, "caixa1");

        // 10% só sobre a água (R$ 20): nem a sessão (R$ 30) nem a compra na loja (R$ 10).
        assertThat(order.serviceFeeAmount()).isEqualByComparingTo("2.00");
    }
}
