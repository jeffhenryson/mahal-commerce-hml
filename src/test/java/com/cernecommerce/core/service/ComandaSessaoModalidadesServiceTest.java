package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionAddonNotAfterDeliveryException;
import com.cernecommerce.core.domain.exception.pdv.SessionCollectedException;
import com.cernecommerce.core.domain.exception.pdv.SessionEssenceNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.SessionFlavorNotInPackagesException;
import com.cernecommerce.core.domain.exception.pdv.SessionLinkedLineException;
import com.cernecommerce.core.domain.exception.pdv.SessionModalityDuploException;
import com.cernecommerce.core.domain.exception.pdv.SessionPackagePaidException;
import com.cernecommerce.core.domain.exception.pdv.SessionPackagesRequiredException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.SessionModalityFlags;
import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionLink;
import com.cernecommerce.core.domain.model.pdv.SessionModality;
import com.cernecommerce.core.domain.model.pdv.SessionProgress;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionSetup;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase.PackageChoice;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PDV-F061 — modalidades da sessão (compra de essência, itens pessoais) com pacotes da loja, e adicional
 * depois da entrega.
 */
@ExtendWith(MockitoExtension.class)
class ComandaSessaoModalidadesServiceTest {

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

    private static final SessionTier TRADICIONAL = new SessionTier(1L, "Tradicional", new BigDecimal("25.00"),
            "Zgy, Zomo, Pred", 1, true);
    private static final SessionTier PREMIUM = new SessionTier(2L, "Premium", new BigDecimal("30.00"),
            "Sence", 2, true);
    private static final SessionSettings SETTINGS = new SessionSettings("VASO_P", "VASO_G",
            new BigDecimal("10.00"), Set.of(), new BigDecimal("2.00"), new BigDecimal("60.00"),
            new BigDecimal("5.00"));
    private static final List<SessionAssetType> KIT_PADRAO = List.of(
            new SessionAssetType(1L, "VASO_P", "Vaso pequeno", 5, false, true));
    private static final Pricing PACOTE_45 = Pricing.of(new BigDecimal("30.00"), null, new BigDecimal("45.00"));
    private static final SessionModalityFlags TODAS = new SessionModalityFlags(true, true);
    private static final SessionAddon FILTRO = new SessionAddon(7L, "Filtro de gelo", new BigDecimal("5.00"), 1,
            true, true);
    private static final SessionAddon CARVAO_EXTRA = new SessionAddon(8L, "Carvão extra", new BigDecimal("3.00"), 2,
            true, false);

    private static final String A = "ZOMO-UVA";
    private static final String B = "ZOMO-MENTA";
    private static final String C = "SENCE-LOVE";

    @BeforeEach
    void setUp() {
        comandaService = new ComandaService(comandaRepository, estoqueUseCase, orderRepository,
                orderPaymentRepository, cashbackUseCase, pdvService, notificationUseCase, userRepository,
                BigDecimal.TEN, kitBuilderUseCase, sessionMenu, false);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────────────────

    private static Comanda comanda(ComandaItem... items) {
        return Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", null, ComandaStatus.ABERTA, List.of(items), null,
                "caixa1", Instant.now(), null);
    }

    private static EstoqueUseCase.CatalogSaleInfo essencia(String nome, Long tierId, SessionModalityFlags flags) {
        return new EstoqueUseCase.CatalogSaleInfo(nome, PACOTE_45, true, true, null, 5, false, false, tierId, null,
                flags);
    }

    private void givenEssence(String sku, Long tierId) {
        when(estoqueUseCase.resolveSaleInfo(sku)).thenReturn(essencia(sku, tierId, TODAS));
    }

    private static ComandaUseCase.AddSessionCommand lancar(SessionModality modalidade, String sku, String sku2,
            boolean vasoGrande, Charcoal carvao, boolean duplo, List<PackageChoice> pacotes) {
        return new ComandaUseCase.AddSessionCommand(null, null, vasoGrande, carvao, List.of(), duplo, null, null,
                false, sku, null, sku2, null, null, false, modalidade, pacotes);
    }

    private static ComandaItem withId(ComandaItem i, Long id) {
        return ComandaItem.of(id, i.sku(), i.quantity(), i.unitPrice(), i.costPrice(), i.productName(), i.addedAt(),
                i.mode(), i.courtesy(), i.linkedItemId(), i.notes(), i.surchargeAmount(), i.closedInOrderId(),
                i.packageUses(), i.packageSessionsPerUnit(), i.kitBundleId(), i.kitTemplateId(),
                i.kitDiscountAmount(), i.session(), i.setup(), i.essenceSku(), i.essenceSku2(), i.essence2Package(),
                i.essenceRatio(), i.storePurchase(), i.storeDiscountAmount(), i.promotionId(), i.promotionName(),
                i.promotionDiscount(), i.modality(), i.sessionLink());
    }

    /** O save falso dá id às linhas novas (100, 101, …), preservando modalidade e vínculo. */
    private void givenSaveAssignsItemIds() {
        when(comandaRepository.save(any())).thenAnswer(inv -> {
            Comanda c = inv.getArgument(0);
            List<ComandaItem> items = new ArrayList<>();
            long next = 100 + c.items().stream().filter(i -> i.id() != null && i.id() >= 100).count();
            for (ComandaItem i : c.items()) {
                items.add(i.id() != null ? i : withId(i, next++));
            }
            return Comanda.of(c.id(), c.sessionId(), c.warehouseCode(), c.tableOrCustomerLabel(), c.customerId(),
                    c.status(), items, c.orderId(), c.openedBy(), c.openedAt(), c.closedAt());
        });
    }

    /** Uma sessão já gravada, no status pedido, paga ({@code orderId}) ou não. */
    private static ComandaItem sessao(Long id, SessionModality modalidade, SessionStatus status, Long orderId) {
        Instant t = Instant.now();
        SessionProgress progress = switch (status) {
            case AGUARDANDO_PAGAMENTO -> SessionProgress.awaitingPayment();
            case PREPARANDO -> SessionProgress.preparing(t);
            case ENTREGUE -> new SessionProgress(SessionStatus.ENTREGUE, t, t, null);
            case RECOLHIDO -> new SessionProgress(SessionStatus.RECOLHIDO, t, t, t);
            case NA_FILA -> SessionProgress.queued();
        };
        return ComandaItem.of(id, TRADICIONAL.sku(), BigDecimal.ONE, new BigDecimal("20.00"), null,
                "Sessão Tradicional", t, ConsumptionMode.SESSAO, false, null, "Zomo Uva", null, orderId, null, null,
                null, null, null, progress, null, A, null, false, null, false, null, null, null, null,
                modalidade, null);
    }

    private static ComandaItem pacote(Long id, Long sessionId, Long orderId, String sku, int quantidade) {
        return ComandaItem.of(id, sku, BigDecimal.valueOf(quantidade), new BigDecimal("45.00"),
                new BigDecimal("30.00"), "Pacote " + sku + " (sessão)", Instant.now(), ConsumptionMode.NORMAL, false,
                sessionId, null, null, orderId, null, null, null, null, null, null, null, null, null, false, null,
                false, null, null, null, null, null, SessionLink.PACOTE_ESSENCIA);
    }

    private void verifyStock(String sku, MovementType tipo, int quantidade) {
        verify(estoqueUseCase).adjustStock(eq(sku), eq("LOJA-01"), eq(tipo), eq(BigDecimal.valueOf(quantidade)),
                any(), eq("caixa1"));
    }

    // ── Compra de essência ───────────────────────────────────────────────────────────────────

    /** Checklist do front: 2× A, B e C, mescla A + C. */
    @Test
    void compraEssencia_variosPacotes_todosCobrados_sessaoPelaMaiorFaixaDaMescla() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenEssence(A, 1L);
        givenEssence(B, 1L);
        givenEssence(C, 2L);
        when(sessionMenu.findActiveTier(1L)).thenReturn(Optional.of(TRADICIONAL));
        when(sessionMenu.findActiveTier(2L)).thenReturn(Optional.of(PREMIUM));
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.reserveAssetsForSession(SETTINGS, false)).thenReturn(KIT_PADRAO);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.addSession(10L, lancar(SessionModality.COMPRA_ESSENCIA, A, C, false, null,
                false, List.of(new PackageChoice(A, 2), new PackageChoice(B, 1), new PackageChoice(C, 1))),
                "caixa1");

        ComandaItem sessao = result.items().get(0);
        assertThat(sessao.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(sessao.modality()).isEqualTo(SessionModality.COMPRA_ESSENCIA);
        assertThat(sessao.essenceSku()).isEqualTo(A);
        assertThat(sessao.essenceSku2()).isEqualTo(C);
        assertThat(sessao.packageUses()).isNull();
        List<ComandaItem> pacotes = result.items().subList(1, result.items().size());
        assertThat(pacotes).extracting(ComandaItem::sku).containsExactly(A, B, C);
        assertThat(pacotes).extracting(i -> i.quantity().intValue()).containsExactly(2, 1, 1);
        assertThat(pacotes).allSatisfy(p -> {
            assertThat(p.sessionLink()).isEqualTo(SessionLink.PACOTE_ESSENCIA);
            assertThat(p.linkedItemId()).isEqualTo(sessao.id());
            assertThat(p.unitPrice()).isEqualByComparingTo("45.00");
        });
        verifyStock(A, MovementType.SAIDA, 2);
        verifyStock(B, MovementType.SAIDA, 1);
        verifyStock(C, MovementType.SAIDA, 1);
        verify(estoqueUseCase, never()).consumeSession(any(), any(), any(), any());
        verify(sessionMenu).allocate(sessao.id(), KIT_PADRAO);
    }

    @Test
    void semPacotes_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenEssence(A, 1L);

        assertThatThrownBy(() -> comandaService.addSession(10L,
                lancar(SessionModality.COMPRA_ESSENCIA, A, null, false, null, false, List.of()), "caixa1"))
                .isInstanceOf(SessionPackagesRequiredException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void saborForaDosPacotes_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenEssence(A, 1L);
        givenEssence(B, 1L);

        assertThatThrownBy(() -> comandaService.addSession(10L, lancar(SessionModality.ITENS_PESSOAIS, A, null,
                false, null, false, List.of(new PackageChoice(B, 1))), "caixa1"))
                .isInstanceOf(SessionFlavorNotInPackagesException.class);
        verify(comandaRepository, never()).save(any());
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void pacoteForaDaModalidade_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenEssence(A, 1L);
        when(estoqueUseCase.resolveSaleInfo(C)).thenReturn(essencia(C, 2L, new SessionModalityFlags(false, true)));

        assertThatThrownBy(() -> comandaService.addSession(10L, lancar(SessionModality.COMPRA_ESSENCIA, A, null,
                false, null, false, List.of(new PackageChoice(A, 1), new PackageChoice(C, 1))), "caixa1"))
                .isInstanceOf(SessionEssenceNotAllowedException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void modalidadeNova_comDuplo_eRecusada() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenEssence(A, 1L);

        assertThatThrownBy(() -> comandaService.addSession(10L, lancar(SessionModality.ITENS_PESSOAIS, A, null,
                false, null, true, List.of(new PackageChoice(A, 1))), "caixa1"))
                .isInstanceOf(SessionModalityDuploException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void tornarDuplo_emSessaoDeModalidadeNova_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(
                comanda(sessao(1L, SessionModality.ITENS_PESSOAIS, SessionStatus.PREPARANDO, 50L))));

        assertThatThrownBy(() -> comandaService.convertToDuplo(10L, 1L, "caixa1"))
                .isInstanceOf(SessionModalityDuploException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void roshExtra_emSessaoDeModalidadeNova_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(
                comanda(sessao(1L, SessionModality.COMPRA_ESSENCIA, SessionStatus.PREPARANDO, 50L))));

        assertThatThrownBy(() -> comandaService.addRoshExtra(10L, 1L, null, null, null, "caixa1"))
                .isInstanceOf(SessionModalityDuploException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void removerOPacoteSozinho_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessao(1L, SessionModality.COMPRA_ESSENCIA, SessionStatus.AGUARDANDO_PAGAMENTO, null),
                pacote(2L, 1L, null, A, 1))));

        assertThatThrownBy(() -> comandaService.removeItem(10L, 2L, "caixa1"))
                .isInstanceOf(SessionLinkedLineException.class);
    }

    @Test
    void removerASessao_levaOsPacotesEDevolveALoja() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessao(1L, SessionModality.COMPRA_ESSENCIA, SessionStatus.AGUARDANDO_PAGAMENTO, null),
                pacote(2L, 1L, null, A, 2), pacote(3L, 1L, null, B, 1))));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda result = comandaService.removeItem(10L, 1L, "caixa1");

        assertThat(result.items()).isEmpty();
        verifyStock(A, MovementType.ENTRADA, 2);
        verifyStock(B, MovementType.ENTRADA, 1);
        // A sessão carimbou o sabor sem gastar lata: não há uso a devolver.
        verify(estoqueUseCase, never()).releaseSession(any(), any(), any());
    }

    // ── Edição ───────────────────────────────────────────────────────────────────────────────

    @Test
    void editar_pacotes_moveSoADiferencaERefazAsLinhas() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessao(1L, SessionModality.COMPRA_ESSENCIA, SessionStatus.AGUARDANDO_PAGAMENTO, null),
                pacote(2L, 1L, null, A, 2))));
        givenEssence(A, 1L);
        givenEssence(B, 1L);
        when(sessionMenu.findActiveTier(1L)).thenReturn(Optional.of(TRADICIONAL));
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda result = comandaService.editSession(10L, 1L, new ComandaUseCase.EditSessionCommand(
                null, null, A, null, null, false, null, false, List.of(),
                List.of(new PackageChoice(A, 1), new PackageChoice(B, 1))), "caixa1").comanda();

        verifyStock(A, MovementType.ENTRADA, 1);
        verifyStock(B, MovementType.SAIDA, 1);
        assertThat(result.items()).filteredOn(ComandaItem::isPackageLine)
                .extracting(ComandaItem::sku, i -> i.quantity().intValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(A, 1), org.assertj.core.groups.Tuple.tuple(B, 1));
    }

    @Test
    void editar_pacotesComPacotePago_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessao(1L, SessionModality.COMPRA_ESSENCIA, SessionStatus.PREPARANDO, 50L),
                pacote(2L, 1L, 50L, A, 1))));
        givenEssence(A, 1L);
        givenEssence(B, 1L);

        assertThatThrownBy(() -> comandaService.editSession(10L, 1L, new ComandaUseCase.EditSessionCommand(
                null, null, A, null, null, false, null, false, List.of(),
                List.of(new PackageChoice(A, 1), new PackageChoice(B, 1))), "caixa1"))
                .isInstanceOf(SessionPackagePaidException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    // ── Repetir ──────────────────────────────────────────────────────────────────────────────

    @Test
    void repetir_comEssenciasCompradas_mesmaModalidade_semPacoteNovo() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessao(1L, SessionModality.COMPRA_ESSENCIA, SessionStatus.RECOLHIDO, 50L),
                pacote(2L, 1L, 50L, A, 1), pacote(3L, 1L, 50L, B, 1))));
        givenEssence(B, 1L);
        when(sessionMenu.findActiveTier(1L)).thenReturn(Optional.of(TRADICIONAL));
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.reserveAssetsForSession(SETTINGS, false)).thenReturn(KIT_PADRAO);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.repeatSession(10L, 1L, new ComandaUseCase.RepeatSessionCommand(null, false,
                null, null, false, B, null, null, false, ComandaUseCase.RepeatMode.ESSENCIAS_COMPRADAS), "caixa1");

        ComandaItem nova = result.items().get(result.items().size() - 1);
        assertThat(nova.mode()).isEqualTo(ConsumptionMode.SESSAO);
        assertThat(nova.modality()).isEqualTo(SessionModality.COMPRA_ESSENCIA);
        assertThat(nova.unitPrice()).isEqualByComparingTo("20.00");
        assertThat(nova.essenceSku()).isEqualTo(B);
        assertThat(result.items()).hasSize(4);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(estoqueUseCase, never()).consumeSession(any(), any(), any(), any());
    }

    @Test
    void repetir_comEssenciasCompradas_saborNaoComprado_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessao(1L, SessionModality.COMPRA_ESSENCIA, SessionStatus.RECOLHIDO, 50L),
                pacote(2L, 1L, 50L, A, 1))));

        assertThatThrownBy(() -> comandaService.repeatSession(10L, 1L, new ComandaUseCase.RepeatSessionCommand(
                null, false, null, null, false, C, null, null, false, null), "caixa1"))
                .isInstanceOf(SessionFlavorNotInPackagesException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void repetir_comoPadrao_cobraAFaixaEGastaALata() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda(
                sessao(1L, SessionModality.ITENS_PESSOAIS, SessionStatus.RECOLHIDO, 50L))));
        givenEssence(A, 1L);
        when(sessionMenu.findActiveTier(1L)).thenReturn(Optional.of(TRADICIONAL));
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        when(sessionMenu.reserveAssetsForSession(SETTINGS, false)).thenReturn(KIT_PADRAO);
        when(estoqueUseCase.consumeSession(A, "LOJA-01", BigDecimal.ONE, "caixa1"))
                .thenReturn(OpenPackage.registered(A, 1L, 5, 1, "caixa1", Instant.now()).withUses(1));
        givenSaveAssignsItemIds();

        Comanda result = comandaService.repeatSession(10L, 1L, new ComandaUseCase.RepeatSessionCommand(null, false,
                null, null, false, null, null, null, false, ComandaUseCase.RepeatMode.PADRAO), "caixa1");

        ComandaItem nova = result.items().get(1);
        assertThat(nova.effectiveModality()).isEqualTo(SessionModality.PADRAO);
        assertThat(nova.unitPrice()).isEqualByComparingTo("25.00");
        verify(estoqueUseCase).consumeSession(A, "LOJA-01", BigDecimal.ONE, "caixa1");
    }

    // ── Itens pessoais ───────────────────────────────────────────────────────────────────────

    /** Checklist do front: 2× A e B, mescla A + B. */
    @Test
    void itensPessoais_umPacoteIncluso_restoCobrado_semUtensilioNemLata() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenEssence(A, 1L);
        givenEssence(B, 1L);
        when(sessionMenu.findActiveTier(1L)).thenReturn(Optional.of(TRADICIONAL));
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        givenSaveAssignsItemIds();

        // vasoGrande=true é ignorado: o narguilé é do cliente.
        Comanda result = comandaService.addSession(10L, lancar(SessionModality.ITENS_PESSOAIS, A, B, true, null,
                false, List.of(new PackageChoice(A, 2), new PackageChoice(B, 1))), "caixa1");

        ComandaItem sessao = result.items().get(0);
        assertThat(sessao.unitPrice()).isEqualByComparingTo("60.00");
        assertThat(sessao.setup()).isNull();
        ComandaItem incluso = result.items().get(1);
        assertThat(incluso.sku()).isEqualTo(A);
        assertThat(incluso.isIncludedPackage()).isTrue();
        assertThat(incluso.unitPrice()).isEqualByComparingTo("0");
        assertThat(incluso.quantity()).isEqualByComparingTo("1");
        assertThat(result.items().subList(2, 4))
                .extracting(ComandaItem::sku, i -> i.quantity().intValue(), ComandaItem::unitPrice)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(A, 1, new BigDecimal("45.00")),
                        org.assertj.core.groups.Tuple.tuple(B, 1, new BigDecimal("45.00")));
        verifyStock(A, MovementType.SAIDA, 2);
        verifyStock(B, MovementType.SAIDA, 1);
        verify(sessionMenu, never()).reserveAssetsForSession(any(), anyBoolean());
        verify(estoqueUseCase, never()).consumeSession(any(), any(), any(), any());
    }

    @Test
    void itensPessoais_semFaixa_usaOSkuSintetico() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda()));
        givenEssence(A, null);
        when(sessionMenu.settings()).thenReturn(SETTINGS);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.addSession(10L, lancar(SessionModality.ITENS_PESSOAIS, A, null, false,
                Charcoal.JUMBO, false, List.of(new PackageChoice(A, 1))), "caixa1");

        ComandaItem sessao = result.items().get(0);
        assertThat(sessao.sku()).isEqualTo(SessionModality.ITENS_PESSOAIS_SKU);
        assertThat(sessao.sessionTierId()).isNull();
        assertThat(sessao.unitPrice()).isEqualByComparingTo("62.00");
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(1).isIncludedPackage()).isTrue();
    }

    // ── Adicional depois da entrega ──────────────────────────────────────────────────────────

    @Test
    void adicional_naSessaoEntregueEPaga_viraLinhaNovaAPagar() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(
                comanda(sessao(1L, SessionModality.PADRAO, SessionStatus.ENTREGUE, 50L))));
        when(sessionMenu.requireActiveAddon(7L)).thenReturn(FILTRO);
        givenSaveAssignsItemIds();

        Comanda result = comandaService.addSessionAddon(10L, 1L, 7L, "caixa1");

        assertThat(result.items().get(0).unitPrice()).isEqualByComparingTo("20.00");
        ComandaItem linha = result.items().get(1);
        assertThat(linha.mode()).isEqualTo(ConsumptionMode.ADICIONAL_SESSAO);
        assertThat(linha.sessionLink()).isEqualTo(SessionLink.ADICIONAL);
        assertThat(linha.linkedItemId()).isEqualTo(1L);
        assertThat(linha.unitPrice()).isEqualByComparingTo("5.00");
        assertThat(linha.productName()).isEqualTo("Filtro de gelo");
        assertThat(linha.isOpen()).isTrue();
        assertThat(linha.consumedStock()).isFalse();
    }

    @Test
    void adicional_naSessaoEntregueSemPedido_somaAoSubtotalDela() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(
                comanda(sessao(1L, SessionModality.PADRAO, SessionStatus.ENTREGUE, null))));
        when(sessionMenu.requireActiveAddon(7L)).thenReturn(FILTRO);
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda result = comandaService.addSessionAddon(10L, 1L, 7L, "caixa1");

        assertThat(result.items()).hasSize(1);
        ComandaItem sessao = result.items().get(0);
        assertThat(sessao.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(sessao.setup().addons()).extracting(SessionSetup.Addon::addonId).containsExactly(7L);
    }

    @Test
    void adicional_naSessaoRecolhida_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(
                comanda(sessao(1L, SessionModality.PADRAO, SessionStatus.RECOLHIDO, 50L))));

        assertThatThrownBy(() -> comandaService.addSessionAddon(10L, 1L, 7L, "caixa1"))
                .isInstanceOf(SessionCollectedException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void adicional_semAMarcaPosEntrega_naSessaoEntregue_eRecusado() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(
                comanda(sessao(1L, SessionModality.PADRAO, SessionStatus.ENTREGUE, 50L))));
        when(sessionMenu.requireActiveAddon(8L)).thenReturn(CARVAO_EXTRA);

        assertThatThrownBy(() -> comandaService.addSessionAddon(10L, 1L, 8L, "caixa1"))
                .isInstanceOf(SessionAddonNotAfterDeliveryException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void adicional_semAMarca_antesDaEntrega_vale() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(
                comanda(sessao(1L, SessionModality.PADRAO, SessionStatus.PREPARANDO, null))));
        when(sessionMenu.requireActiveAddon(8L)).thenReturn(CARVAO_EXTRA);
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda result = comandaService.addSessionAddon(10L, 1L, 8L, "caixa1");

        assertThat(result.items().get(0).unitPrice()).isEqualByComparingTo("23.00");
        verify(comandaRepository).save(eq(result));
    }
}
