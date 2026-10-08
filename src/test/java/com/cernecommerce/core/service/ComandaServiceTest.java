package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.ItemNotOpenInComandaException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemMustCloseTogetherException;
import com.cernecommerce.core.domain.exception.pdv.ComandaPartiallyClosedException;
import com.cernecommerce.core.domain.exception.pdv.ComandaMergeNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException;
import com.cernecommerce.core.domain.exception.pdv.ComandaOnlyCourtesyException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemRequiredException;
import com.cernecommerce.core.domain.exception.pdv.NotASessionProductException;
import com.cernecommerce.core.domain.exception.pdv.NotAnOpenRoshException;
import com.cernecommerce.core.domain.exception.pdv.NotAvailableForTableException;
import com.cernecommerce.core.domain.exception.pdv.NotesTooLongException;
import com.cernecommerce.core.domain.exception.pdv.OpenRoshNotPricedException;
import com.cernecommerce.core.domain.exception.pdv.SurchargeInvalidException;
import com.cernecommerce.core.domain.exception.pdv.SurchargeNotApplicableException;
import com.cernecommerce.core.domain.exception.pdv.SurchargeOnCourtesyException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.exception.pdv.ComandaItemNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemIsChargedException;
import com.cernecommerce.core.domain.exception.pdv.DiscountExceedsBillException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pdv.ClosedComanda;
import com.cernecommerce.core.domain.model.pdv.SessionProgress;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pdv.StorePurchase;
import com.cernecommerce.core.domain.model.pdv.ComandaHistoryFilter;
import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.domain.exception.pdv.KitItemRemovalNotAllowedException;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.KitTemplateStep;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.out.user.UserRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComandaServiceTest {

    private static final Pricing ESSENCIA = Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00"));

    @Mock ComandaRepository comandaRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock PdvService pdvService;
    @Mock NotificationUseCase notificationUseCase;
    @Mock UserRepository userRepository;
    @Mock KitBuilderUseCase kitBuilderUseCase;

    ComandaService comandaService;

    /** Os 10% do salão (PDV-F015). Os testes que não são sobre a taxa fecham com applyServiceFee=false. */
    private static final BigDecimal SERVICE_FEE_PERCENT = new BigDecimal("10");

    @BeforeEach
    void setUp() {
        comandaService = new ComandaService(comandaRepository, estoqueUseCase, orderRepository,
                orderPaymentRepository, cashbackUseCase, pdvService, notificationUseCase, userRepository,
                SERVICE_FEE_PERCENT, kitBuilderUseCase);
    }

    private CashRegisterSession openSession() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
    }

    private Comanda abertaComanda(ComandaItem... items) {
        Comanda comanda = Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1");
        for (ComandaItem item : items) {
            comanda = comanda.withAddedItem(item);
        }
        return Comanda.of(10L, comanda.sessionId(), comanda.warehouseCode(), comanda.tableOrCustomerLabel(),
                comanda.customerId(), comanda.status(), comanda.items(), comanda.orderId(), comanda.openedBy(),
                comanda.openedAt(), comanda.closedAt());
    }

    private static ComandaItem essenciaItem() {
        return ComandaItem.fromCatalog("ESS-MENTA", BigDecimal.ONE, ESSENCIA, "Essência Menta");
    }

    private void givenOrderPersistenceAssignsId() {
        when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order arg = inv.getArgument(0);
            // Sobrecarga COMPLETA de propósito: a curta perderia comandaId/tableLabel, e pedido
            // de MESA sem eles é recusado pelo compact constructor de Order.
            return Order.of(500L, arg.orderNumber(), arg.channel(), arg.status(), arg.customerId(),
                    arg.sessionId(), arg.warehouseCode(), arg.items(), arg.grossAmount(), arg.discountAmount(),
                    arg.cashbackRedeemed(), arg.netAmount(), arg.changeAmount(), arg.cancelReason(),
                    arg.createdAt(), arg.paidAt(), arg.concludedAt(), arg.cancelledAt(), arg.refundedAt(),
                    arg.reservedAt(), arg.separatedAt(), arg.shippedAt(), arg.deliveredAt(), arg.version(),
                    // PDV-F015: a taxa TEM que atravessar o save falso. Sem ela aqui, o fake
                    // devolveria o pedido sem taxa e todo teste de taxa passaria por engano.
                    arg.comandaId(), arg.tableLabel(), arg.serviceFeeAmount());
        });
    }

    // ── Abertura ─────────────────────────────────────────────────────────────────────────────

    @Test
    void openComanda_opensAtTheSessionWarehouse() {
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda comanda = comandaService.openComanda(1L, "Mesa 4", "caixa1");

        assertThat(comanda.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(comanda.status()).isEqualTo(ComandaStatus.ABERTA);
        verify(pdvService).requireOwnOpenSession(1L, "caixa1");
    }

    @Test
    void openComanda_propagatesOwnershipFailureAndDoesNotSave() {
        when(pdvService.requireOwnOpenSession(1L, "outro-operador"))
                .thenThrow(new com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException(1L, "outro-operador"));

        assertThatThrownBy(() -> comandaService.openComanda(1L, "Mesa 4", "outro-operador"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── Lançamento de item ───────────────────────────────────────────────────────────────────

    @Test
    void addItem_resolvesPriceFromCatalogAndDebitsStockImmediately() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1");

        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        verify(estoqueUseCase).adjustStock(eq("ESS-MENTA"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(BigDecimal.ONE), eq("Comanda #10"), eq("caixa1"));
    }

    @Test
    void addItem_refusesOnNonAbertaComandaAndDoesNotTouchStock() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(essenciaItem()), 500L, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fechada));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addItem_throwsWhenComandaNotFound() {
        when(comandaRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comandaService.addItem(999L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ComandaNotFoundException.class);
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void addItem_propagatesUnknownSkuAndDoesNotSave() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("DESCONHECIDO")).thenThrow(new ProductNotFoundException("DESCONHECIDO"));

        assertThatThrownBy(() -> comandaService.addItem(10L, "DESCONHECIDO", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ProductNotFoundException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void addItem_propagatesInsufficientStockAndDoesNotSave() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA));
        doThrow(new InsufficientStockException("ESS-MENTA", 1L, BigDecimal.ZERO, BigDecimal.ONE))
                .when(estoqueUseCase).adjustStock(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(InsufficientStockException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── Remoção de item (PDV-F012) ───────────────────────────────────────────────────────────

    private static ComandaItem linha(Long id, String sku, String unitPrice, ConsumptionMode mode,
            boolean courtesy, Long linkedItemId) {
        return ComandaItem.of(id, sku, BigDecimal.ONE, new BigDecimal(unitPrice), new BigDecimal("5.00"),
                sku, Instant.now(), mode, courtesy, linkedItemId);
    }

    private Comanda comandaComSessaoETroca() {
        return abertaComanda(
                linha(1L, "SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null),
                linha(2L, "ESS-UVA", "0.00", ConsumptionMode.TROCA, true, 1L),
                linha(3L, "BEB-COLA", "12.00", ConsumptionMode.NORMAL, false, null));
    }

    @Test
    void removeItem_returnsStockForTheLineAndForTheTrocasDraggedWithIt() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaComSessaoETroca()));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda result = comandaService.removeItem(10L, 1L, "caixa1");

        assertThat(result.items()).extracting(ComandaItem::id).containsExactly(3L);
        // Uma ENTRADA por linha removida — a sessão E a troca. A cortesia também volta: o cliente
        // não pagou por ela, mas a essência tinha saído do estoque.
        verify(estoqueUseCase).adjustStock(eq("SESS-BLUE"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), contains("Remoção de item da comanda #10"), eq("caixa1"));
        verify(estoqueUseCase).adjustStock(eq("ESS-UVA"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), contains("Remoção de item da comanda #10"), eq("caixa1"));
        verify(estoqueUseCase, times(2)).adjustStock(any(), any(), eq(MovementType.ENTRADA), any(),
                any(), any());
    }

    @Test
    void removeItem_removingALeafLineReturnsOnlyItsOwnStock() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaComSessaoETroca()));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda result = comandaService.removeItem(10L, 3L, "caixa1");

        assertThat(result.items()).extracting(ComandaItem::id).containsExactly(1L, 2L);
        verify(estoqueUseCase, times(1)).adjustStock(any(), any(), eq(MovementType.ENTRADA), any(),
                any(), any());
    }

    /**
     * O sabor extra pode estar cobrado: arrastá-lo tiraria valor da conta sem ninguém pedir. A
     * recusa vem <b>antes</b> de tocar no estoque — cada devolução é seu próprio efeito, e abortar
     * no meio deixaria saldo devolvido sem a linha ter saído.
     */
    @Test
    void removeItem_refusesWhenAChargedSaborExtraHangsOnTheLine_beforeTouchingStock() {
        Comanda comanda = abertaComanda(
                linha(1L, "SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null),
                linha(2L, "SESS-MANGA", "35.00", ConsumptionMode.SABOR_EXTRA, false, 1L));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));

        assertThatThrownBy(() -> comandaService.removeItem(10L, 1L, "caixa1"))
                .isInstanceOf(LinkedItemIsChargedException.class)
                .hasMessageContaining("2");

        verifyNoInteractions(estoqueUseCase);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void removeItem_refusesAnItemFromAnotherComanda() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaComSessaoETroca()));

        assertThatThrownBy(() -> comandaService.removeItem(10L, 999L, "caixa1"))
                .isInstanceOf(ComandaItemNotFoundException.class);

        verifyNoInteractions(estoqueUseCase);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void removeItem_refusesOnAComandaThatIsNotOpen() {
        Comanda fechada = comandaComSessaoETroca().closed(500L, Instant.now());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fechada));

        assertThatThrownBy(() -> comandaService.removeItem(10L, 1L, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);

        verifyNoInteractions(estoqueUseCase);
    }

    /** PDV-C008 — quarto caminho de mutação, e portanto quarta leitura travada. */
    @Test
    void removeItem_readsTheComandaUnderLock() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaComSessaoETroca()));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.removeItem(10L, 3L, "caixa1");

        verify(comandaRepository).findByIdForUpdate(10L);
        verify(comandaRepository, never()).findById(any());
    }

    /** Mesa compartilhada: remover não exige posse do caixa, mas exige a sessão de ORIGEM aberta. */
    @Test
    void removeItem_requiresTheOriginSessionOpenWithoutOwnership() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaComSessaoETroca()));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.removeItem(10L, 3L, "outro-atendente");

        verify(pdvService).requireOpenSession(1L);
        verify(pdvService, never()).requireOwnOpenSession(any(), any());
    }

    /** Comanda vazia é estado legítimo — é como ela nasce, e COMANDA_EMPTY já barra fechá-la assim. */
    @Test
    void removeItem_canEmptyTheComanda() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(
                linha(1L, "BEB-COLA", "12.00", ConsumptionMode.NORMAL, false, null))));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(comandaService.removeItem(10L, 1L, "caixa1").items()).isEmpty();
    }

    // ── Fechamento ───────────────────────────────────────────────────────────────────────────

    @Test
    void closeComanda_convertsAccumulatedItemsWithoutReQueryingTheCatalog() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        // PDV-F010: fechar não exige posse da comanda, mas exige caixa aberto de quem fecha —
        // é a gaveta dele que recebe o dinheiro.
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), eq(new BigDecimal("25.00"))))
                .thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<PaymentCommand> payments = List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null));
        Order order = comandaService.closeComanda(10L, payments, null, false, "caixa1");

        assertThat(order.id()).isEqualTo(500L);
        // PDV-F010: o pedido NASCE MESA — o canal é imutável, não vira MESA depois.
        assertThat(order.channel()).isEqualTo(SalesChannel.MESA);
        assertThat(order.comandaId()).isEqualTo(10L);
        assertThat(order.tableLabel()).isEqualTo("Mesa 4");
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        // Nunca resolve o preço de novo pelo catálogo — o item já veio precificado da comanda.
        verifyNoInteractions(estoqueUseCase);
        verify(orderPaymentRepository).save(argThat(p -> p.orderId().equals(500L)
                && p.amount().compareTo(new BigDecimal("25.00")) == 0));
        verify(cashbackUseCase).recordEarnedForOrder(any());
        verify(comandaRepository).save(argThat(c -> c.status() == ComandaStatus.FECHADA
                && c.orderId().equals(500L)));
    }

    // ── Desconto no fechamento (PDV-F014) ────────────────────────────────────────────────────

    /** Fixture de duas linhas com valores que rateiam redondo: 70 + 30. */
    private Comanda comandaDeDuasLinhas() {
        return abertaComanda(
                ComandaItem.of(1L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("70.00"),
                        new BigDecimal("20.00"), "Essência Menta", Instant.now()),
                ComandaItem.of(2L, "BEB-COLA", BigDecimal.ONE, new BigDecimal("30.00"),
                        new BigDecimal("10.00"), "Refrigerante", Instant.now()));
    }

    private void givenCloseablePara(Comanda comanda) {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static List<PaymentCommand> dinheiro(String valor) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(valor), null));
    }

    /**
     * O desconto é pedido sobre a conta, mas gravado por item. Sem o rateio, o cashback seria
     * creditado sobre o valor cheio e a margem por item mostraria a venda sem o abatimento.
     */
    /** CRM-F010 — marcar na mesa: mesma validação do balcão, linha ON_ACCOUNT e recebível com a comanda. */
    @Test
    void closeComanda_withOnAccountLine_validatesSavesOnAccountAndCreatesTheReceivable() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), eq(new BigDecimal("25.00")))).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        java.time.LocalDate due = java.time.LocalDate.now().plusDays(7);
        List<PaymentCommand> payments = List.of(
                new PaymentCommand(PaymentMethod.PIX, new BigDecimal("5.00"), null),
                PaymentCommand.onAccount(new BigDecimal("20.00"), due));

        Order order = comandaService.closeComanda(10L, payments, null, false, "caixa1");

        verify(pdvService).validateOnAccount(comanda.customerId(),
                com.cernecommerce.core.domain.model.recebivel.OnAccountChannel.MESA, payments);
        verify(orderPaymentRepository).save(argThat(p -> p.method() == PaymentMethod.MARCADO
                && p.status() == com.cernecommerce.core.domain.model.pagamento.PaymentStatus.ON_ACCOUNT
                && due.equals(p.dueDate())));
        verify(pdvService).recordReceivableIfOnAccount(order, 10L, payments, "caixa1");
    }

    @Test
    void closeComanda_proratesTheBillDiscountAcrossTheItems() {
        givenCloseablePara(comandaDeDuasLinhas());

        Order order = comandaService.closeComanda(10L, dinheiro("90.00"), new BigDecimal("10.00"),
                false, "caixa1");

        assertThat(order.items().get(0).discountAmount()).isEqualByComparingTo("7.00");
        assertThat(order.items().get(1).discountAmount()).isEqualByComparingTo("3.00");
        // A invariante do pedido continua valendo: o desconto do pedido é a soma dos itens.
        assertThat(order.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(order.netAmount()).isEqualByComparingTo("90.00");
    }

    /** Sem desconto, todo item entra com zero — o caminho de sempre não mudou. */
    @Test
    void closeComanda_withoutDiscount_keepsEveryItemAtZero() {
        givenCloseablePara(comandaDeDuasLinhas());

        Order order = comandaService.closeComanda(10L, dinheiro("100.00"), null, false, "caixa1");

        assertThat(order.discountAmount()).isEqualByComparingTo("0");
        assertThat(order.items()).allSatisfy(
                i -> assertThat(i.discountAmount()).isEqualByComparingTo("0"));
    }

    /** O teto é o mesmo do balcão, e é o do balcão que decide — não uma cópia da regra. */
    @Test
    void closeComanda_checksTheDiscountAgainstTheSameLimitAsTheCounterSale() {
        givenCloseablePara(comandaDeDuasLinhas());

        comandaService.closeComanda(10L, dinheiro("90.00"), new BigDecimal("10.00"), false, "caixa1");

        verify(pdvService).requireDiscountWithinLimit(argThat(
                o -> o.discountAmount().compareTo(new BigDecimal("10.00")) == 0));
    }

    /** Desconto acima do teto aborta antes de gravar pedido ou pagamento. */
    @Test
    void closeComanda_refusesDiscountAboveTheLimitBeforeSavingAnything() {
        Comanda comanda = comandaDeDuasLinhas();
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(cashbackUseCase.resolveApplicableRate(any())).thenReturn(null);
        doThrow(new DiscountLimitExceededException(new BigDecimal("50"), new BigDecimal("10")))
                .when(pdvService).requireDiscountWithinLimit(any());

        assertThatThrownBy(() -> comandaService.closeComanda(10L, dinheiro("50.00"),
                new BigDecimal("50.00"), false, "caixa1"))
                .isInstanceOf(DiscountLimitExceededException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderPaymentRepository);
    }

    /**
     * PDV-C016 — desconto maior que a própria conta responde 409, não 500.
     *
     * <p>{@code DiscountProration.distribute} já recusava o caso, mas com
     * {@code IllegalArgumentException}, que o handler global achata num 400 genérico. E o problema
     * maior é a <b>ordem</b>: o rateio roda <b>antes</b> de {@code requireDiscountWithinLimit},
     * então o desconto absurdo nunca chegava ao {@code 409 DISCOUNT_LIMIT_EXCEEDED} que a tela já
     * trata — pedir 11% de desconto dava um erro acionável, pedir o dobro da conta dava
     * "Requisição inválida".</p>
     */
    @Test
    void closeComanda_refusesDiscountGreaterThanTheBillBeforeProratingAnything() {
        // Conta de 100,00 (70 + 30) com 150,00 de desconto pedido.
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaDeDuasLinhas()));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.closeComanda(10L, dinheiro("100.00"),
                new BigDecimal("150.00"), false, "caixa1"))
                .isInstanceOf(DiscountExceedsBillException.class);

        // Nem o teto chega a ser consultado: o valor é aritmeticamente impossível antes de ser
        // política comercial.
        verify(pdvService, never()).requireDiscountWithinLimit(any());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderPaymentRepository);
    }

    /** Cortesia tem valor zero: a proporção dela é zero, e ela não absorve desconto nenhum. */
    @Test
    void closeComanda_courtesyLineAbsorbsNoDiscount() {
        givenCloseablePara(abertaComanda(
                ComandaItem.of(1L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("100.00"),
                        new BigDecimal("20.00"), "Essência Menta", Instant.now()),
                ComandaItem.of(2L, "ESS-UVA", BigDecimal.ONE, BigDecimal.ZERO,
                        new BigDecimal("20.00"), "Essência Uva", Instant.now(),
                        ConsumptionMode.TROCA, true, 1L)));

        Order order = comandaService.closeComanda(10L, dinheiro("90.00"), new BigDecimal("10.00"),
                false, "caixa1");

        assertThat(order.items().get(0).discountAmount()).isEqualByComparingTo("10.00");
        assertThat(order.items().get(1).discountAmount()).isEqualByComparingTo("0");
    }

    // ── Taxa de serviço (PDV-F015) ───────────────────────────────────────────────────────────

    /** É o padrão do salão: omitir não pode significar deixar de cobrar. */
    @Test
    void closeComanda_appliesTheServiceFeeByDefault() {
        givenCloseablePara(comandaDeDuasLinhas());

        Order order = comandaService.closeComanda(10L, dinheiro("110.00"), null, true, "caixa1");

        assertThat(order.serviceFeeAmount()).isEqualByComparingTo("10.00");
        assertThat(order.totalPayable()).isEqualByComparingTo("110.00");
    }

    @Test
    void closeComanda_whenTheCustomerRefusesTheFee_chargesOnlyTheGoods() {
        givenCloseablePara(comandaDeDuasLinhas());

        Order order = comandaService.closeComanda(10L, dinheiro("100.00"), null, false, "caixa1");

        assertThat(order.serviceFeeAmount()).isEqualByComparingTo("0");
        assertThat(order.totalPayable()).isEqualByComparingTo("100.00");
    }

    /**
     * A taxa incide sobre o LÍQUIDO, depois do desconto. Cobrar serviço sobre um abatimento que a
     * casa acabou de conceder seria devolver parte dele com a outra mão.
     */
    @Test
    void closeComanda_computesTheServiceFeeAfterTheDiscount() {
        givenCloseablePara(comandaDeDuasLinhas());

        Order order = comandaService.closeComanda(10L, dinheiro("99.00"), new BigDecimal("10.00"),
                true, "caixa1");

        // 100 − 10 = 90 de líquido → 9,00 de taxa, não 10,00.
        assertThat(order.netAmount()).isEqualByComparingTo("90.00");
        assertThat(order.serviceFeeAmount()).isEqualByComparingTo("9.00");
        assertThat(order.totalPayable()).isEqualByComparingTo("99.00");
    }

    /**
     * O que o pagamento tem que cobrir é o total COM a taxa. Validar contra o líquido deixaria a
     * conta fechar com menos dinheiro do que o cliente deve.
     */
    @Test
    void closeComanda_validatesThePaymentAgainstTotalPayableNotNetAmount() {
        givenCloseablePara(comandaDeDuasLinhas());

        comandaService.closeComanda(10L, dinheiro("110.00"), null, true, "caixa1");

        verify(pdvService).validatePaymentsAndComputeChange(any(), eq(new BigDecimal("110.00")));
    }

    /** A taxa é do garçom: o líquido, que quatro agregações somam como receita, não a enxerga. */
    @Test
    void closeComanda_serviceFeeStaysOutOfTheRevenueFigure() {
        givenCloseablePara(comandaDeDuasLinhas());

        Order order = comandaService.closeComanda(10L, dinheiro("110.00"), null, true, "caixa1");

        assertThat(order.netAmount()).isEqualByComparingTo("100.00");
        assertThat(order.grossAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void closeComanda_doesNotAdjustStockAgain() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        // PDV-F010: fechar não exige posse da comanda, mas exige caixa aberto de quem fecha —
        // é a gaveta dele que recebe o dinheiro.
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null)), null, false, "caixa1");

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        // PDV-F029 — o fechamento total registra quem encerrou a mesa.
        verify(comandaRepository).recordClosing(10L, "caixa1", null);
    }

    @Test
    void closeComanda_refusesEmptyComandaBeforeTouchingOrders() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));

        assertThatThrownBy(() -> comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null)), null, false, "caixa1"))
                .isInstanceOf(ComandaEmptyException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void closeComanda_refusesNonAbertaComanda() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA,
                List.of(essenciaItem()), null, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fechada));

        assertThatThrownBy(() -> comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null)), null, false, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verifyNoInteractions(orderRepository);
    }

    // ── Cancelamento ─────────────────────────────────────────────────────────────────────────

    @Test
    void cancelComanda_returnsStockPerItem() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda cancelada = comandaService.cancelComanda(10L, "caixa1");

        assertThat(cancelada.status()).isEqualTo(ComandaStatus.CANCELADA);
        verify(estoqueUseCase).adjustStock(eq("ESS-MENTA"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(BigDecimal.ONE), eq("Cancelamento de comanda #10"), eq("caixa1"));
        verify(comandaRepository).recordClosing(10L, "caixa1", null);
    }

    /** PDV-F029 — o motivo vai para o histórico aparado; em branco vale como ausente. */
    @Test
    void cancelComanda_recordsWhoCancelledAndTheTrimmedReason() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.cancelComanda(10L, "caixa1", "  Cliente desistiu  ");

        verify(comandaRepository).recordClosing(10L, "caixa1", "Cliente desistiu");
    }

    @Test
    void cancelComanda_blankReasonIsRecordedAsNull() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.cancelComanda(10L, "caixa1", "   ");

        verify(comandaRepository).recordClosing(10L, "caixa1", null);
    }

    @Test
    void cancelComanda_refusesNonAbertaComanda() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(essenciaItem()), 500L, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fechada));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.cancelComanda(10L, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verifyNoInteractions(estoqueUseCase);
    }

    // ── Leitura ──────────────────────────────────────────────────────────────────────────────

    /**
     * A leitura de tela usa {@code findById}, <b>sem</b> a trava de PDV-C008: quem só desenha a
     * comanda não decide nada sobre ela, e travar a linha a cada refresh do salão seguraria a mesa
     * contra quem quer lançar item nela.
     */
    @Test
    void getComanda_throwsWhenNotFound() {
        when(comandaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comandaService.getComanda(999L)).isInstanceOf(ComandaNotFoundException.class);
    }

    @Test
    void listOpenComandas_delegatesToRepository() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findOpen(1L, null, 0, 50))
                .thenReturn(new PageResult<>(List.of(comanda), 0, 50, 1, 1));

        assertThat(comandaService.listOpenComandas(1L, null, 0, 50).content()).containsExactly(comanda);
    }

    /**
     * PDV-C007 — sem sessionId a listagem é da loja, e o service repassa o nulo em vez de exigir um
     * caixa. Era essa exigência que obrigava o cliente a uma chamada por sessão aberta.
     */
    @Test
    void listOpenComandas_withoutSessionId_asksTheRepositoryForTheWholeStore() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findOpen(null, null, 0, 50))
                .thenReturn(new PageResult<>(List.of(comanda), 0, 50, 1, 1));

        assertThat(comandaService.listOpenComandas(null, null, 0, 50).content()).containsExactly(comanda);
    }

    @Test
    void listOpenComandas_passesTheWarehouseFilterThrough() {
        when(comandaRepository.findOpen(null, "LOJA-01", 1, 20))
                .thenReturn(new PageResult<>(List.of(), 1, 20, 0, 0));

        assertThat(comandaService.listOpenComandas(null, "LOJA-01", 1, 20).content()).isEmpty();
        verify(comandaRepository).findOpen(null, "LOJA-01", 1, 20);
    }

    // ── Sessão de narguilé (PDV-F010) ────────────────────────────────────────────────────────

    /** Sabor "blueberry": variação de R$ 35, num produto cujo open rosh custa R$ 60. */
    private static final Pricing SABOR_BLUE = Pricing.of(new BigDecimal("12.00"), null, new BigDecimal("35.00"));

    private EstoqueUseCase.CatalogSaleInfo sessao(BigDecimal openRoshPrice) {
        return new EstoqueUseCase.CatalogSaleInfo("Sessão de narguilé", SABOR_BLUE, true, true, openRoshPrice);
    }

    private ComandaItem openRoshLancado() {
        return ComandaItem.of(77L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("60.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.OPEN_ROSH,
                false, null);
    }

    /**
     * A armadilha central da feature: a linha chega com o SKU da VARIAÇÃO (R$ 35), mas o open rosh
     * cobra o preço do produto PAI (R$ 60). Resolver pelo SKU, como nos outros modos, cobraria 35.
     */
    @Test
    void addItem_openRoshChargesParentPriceNotVariantPrice() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, "caixa1");

        ComandaItem lancado = updated.items().get(0);
        assertThat(lancado.unitPrice()).isEqualByComparingTo("60.00");
        assertThat(lancado.mode()).isEqualTo(ConsumptionMode.OPEN_ROSH);
        // O custo continua vindo do catálogo — o SKU serve para saber o que sai do estoque.
        assertThat(lancado.costPrice()).isEqualByComparingTo("12.00");
    }

    /**
     * Cortesia zera o que se cobra, NUNCA o que se gastou: é o custo congelado que faz a margem do
     * pedido mostrar o prejuízo real da promo — a pergunta de negócio por trás do open rosh.
     */
    @Test
    void addItem_courtesyRecordsZeroPriceButFreezesCostNormally() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(openRoshLancado())));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, true, 77L, "caixa1");

        ComandaItem troca = updated.items().get(1);
        assertThat(troca.unitPrice()).isEqualByComparingTo("0.00");
        assertThat(troca.costPrice()).isEqualByComparingTo("12.00");
        assertThat(troca.courtesy()).isTrue();
        assertThat(troca.linkedItemId()).isEqualTo(77L);
        // Cortesia baixa estoque igual: o cliente não paga, mas a essência saiu.
        verify(estoqueUseCase).adjustStock(eq("SESS-MENTA"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(BigDecimal.ONE), any(), eq("caixa1"));
    }

    /** TROCA é cortesia por definição — não depende de o cliente HTTP ter marcado o campo. */
    @Test
    void addItem_trocaIsCourtesyEvenWhenClientDidNotFlagIt() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(openRoshLancado())));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, false, 77L, "caixa1");

        assertThat(updated.items().get(1).courtesy()).isTrue();
        assertThat(updated.items().get(1).unitPrice()).isEqualByComparingTo("0.00");
    }

    /** O segundo sabor sem promo é linha própria, cobrada pelo preço da sua variação. */
    @Test
    void addItem_saborExtraWithoutPromoChargesItsOwnVariantPrice() {
        ComandaItem primeiraLinha = ComandaItem.of(55L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("35.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.NORMAL,
                false, null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(primeiraLinha)));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.SABOR_EXTRA, false, 55L, "caixa1");

        assertThat(updated.items().get(1).unitPrice()).isEqualByComparingTo("35.00");
        assertThat(updated.items().get(1).courtesy()).isFalse();
    }

    @Test
    void addItem_refusesSkuNotAvailableForTableBeforeTouchingStock() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("CIGARRO-01")).thenReturn(
                new EstoqueUseCase.CatalogSaleInfo("Cigarro", ESSENCIA, false, false, null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "CIGARRO-01", BigDecimal.ONE, null, false,
                null, "caixa1"))
                .isInstanceOf(NotAvailableForTableException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void addItem_refusesSessionModeOnProductThatIsNotASession() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA")).thenReturn(
                new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA, true, false, null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, "caixa1"))
                .isInstanceOf(NotASessionProductException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    /** Sem preço de open rosh não há fallback para o preço do sabor — recusa. */
    @Test
    void addItem_refusesOpenRoshOnProductWithoutOpenRoshPrice() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, "caixa1"))
                .isInstanceOf(OpenRoshNotPricedException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addItem_refusesSaborExtraWithoutLinkedItem() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.SABOR_EXTRA, false, null, "caixa1"))
                .isInstanceOf(LinkedItemRequiredException.class);
    }

    /** O id tem que ser de uma linha DESTA comanda — senão uma troca se penduraria na mesa ao lado. */
    @Test
    void addItem_refusesLinkedItemFromAnotherComanda() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(openRoshLancado())));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, true, 999L, "caixa1"))
                .isInstanceOf(LinkedItemRequiredException.class);
    }

    /** Troca cortesia sobre uma sessão comum daria narguilé de graça. */
    @Test
    void addItem_refusesTrocaLinkedToLineThatIsNotOpenRosh() {
        ComandaItem normal = ComandaItem.of(55L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("35.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.NORMAL,
                false, null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(normal)));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, true, 55L, "caixa1"))
                .isInstanceOf(NotAnOpenRoshException.class);
    }

    // ── Fechamento da mesa (PDV-F010) ────────────────────────────────────────────────────────

    /** Sem isto, o histórico da mesa não distingue cortesia de item cobrado. */
    @Test
    void closeComanda_carriesModeAndCourtesyIntoTheOrderItems() {
        ComandaItem cortesia = ComandaItem.of(78L, "SESS-MENTA", BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.TROCA,
                true, 77L);
        when(comandaRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(abertaComanda(openRoshLancado(), cortesia)));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("60.00"), null)), null, false, "caixa1");

        assertThat(order.items().get(0).mode()).isEqualTo(ConsumptionMode.OPEN_ROSH);
        assertThat(order.items().get(1).mode()).isEqualTo(ConsumptionMode.TROCA);
        assertThat(order.items().get(1).courtesy()).isTrue();
        // Cortesia entra a zero no líquido, mas com custo — é o que revela o prejuízo da promo.
        assertThat(order.items().get(1).netAmount()).isEqualByComparingTo("0.00");
        assertThat(order.items().get(1).costPrice()).isEqualByComparingTo("12.00");
    }

    /**
     * Decisão do dono: mesas compartilhadas, mas o dinheiro pertence à gaveta que o recebeu. Quando
     * outro atendente fecha a mesa, o pedido entra na sessão DELE — e não na de quem abriu.
     */
    @Test
    void closeComanda_creditsTheSessionOfWhoeverCloses() {
        CashRegisterSession outroCaixa = CashRegisterSession.of(2L, "caixa2", Instant.now(),
                BigDecimal.TEN, "LOJA-01", null, null, null, null, null, CashRegisterSession.Status.OPEN);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(essenciaItem())));
        when(pdvService.getCurrentSession("caixa2")).thenReturn(outroCaixa);
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null)), null, false, "caixa2");

        assertThat(order.sessionId()).isEqualTo(2L);
        // O depósito continua sendo o da comanda — é de lá que o estoque saiu, item a item.
        assertThat(order.warehouseCode()).isEqualTo("LOJA-01");
    }

    /** Irmã de COMANDA_EMPTY: mesa só de cortesias não vira pedido de R$ 0. */
    @Test
    void closeComanda_refusesComandaMadeOnlyOfCourtesies() {
        ComandaItem cortesia = ComandaItem.of(78L, "SESS-MENTA", BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.TROCA,
                true, null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(cortesia)));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null)), null, false, "caixa1"))
                .isInstanceOf(ComandaOnlyCourtesyException.class);
        verifyNoInteractions(orderRepository);
    }

    /**
     * O cliente vinculado na abertura chega ao pedido — e a taxa vigente é carimbada em cada linha,
     * que é o que de fato gera o cashback. Verificar só que {@code recordEarnedForOrder} foi
     * chamado não bastava: com a taxa nula o ganho sai zero, e foi assim que a mesa passou a
     * entrega inteira sem gerar cashback nenhum (visto por {@code ComandaCashCycleIT}).
     */
    @Test
    void closeComanda_carriesTheComandaCustomerAndStampsTheCashbackRate() {
        Comanda comMesa = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA,
                List.of(essenciaItem()), null, "caixa1", Instant.now(), null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comMesa));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        when(cashbackUseCase.resolveApplicableRate("ESS-MENTA")).thenReturn(new CashbackRate(1L,
                CashbackScope.GLOBAL, null, new BigDecimal("3.00"), true, Instant.now(), null, Instant.now()));
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null)), null, false, "caixa1");

        assertThat(order.customerId()).isEqualTo(42L);
        assertThat(order.items().get(0).cashbackPercent()).isEqualByComparingTo("3.00");
        // 25,00 líquidos x 3% — o ganho que recordEarnedForOrder tem para lançar.
        assertThat(order.totalCashbackEarned()).isEqualByComparingTo("0.75");
        verify(cashbackUseCase).recordEarnedForOrder(any());
    }

    /** Sem taxa vigente para o SKU, a linha vai sem carimbo — e não quebra o fechamento. */
    @Test
    void closeComanda_semTaxaVigente_fechaSemCashback() {
        Comanda comMesa = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA,
                List.of(essenciaItem()), null, "caixa1", Instant.now(), null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comMesa));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        when(cashbackUseCase.resolveApplicableRate("ESS-MENTA")).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null)), null, false, "caixa1");

        assertThat(order.items().get(0).cashbackPercent()).isNull();
        assertThat(order.totalCashbackEarned()).isEqualByComparingTo("0");
    }

    // ── PDV-F011 — componentes da sessão (notes) e acréscimo no open rosh ────────────────────

    /**
     * O acréscimo soma sobre o {@code openRoshPrice} do produto <b>PAI</b> (60), não sobre o preço
     * da variação do sabor (35). É a armadilha do open rosh um nível acima: somar sobre a variante
     * daria 50, um número que "parece" maior e passaria despercebido na conferência.
     */
    @Test
    void addItem_surchargeSumsOverParentOpenRoshPriceNotVariantPrice() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, null, new BigDecimal("15.00"), "caixa1");

        ComandaItem lancado = updated.items().get(0);
        assertThat(lancado.unitPrice()).isEqualByComparingTo("75.00");
        assertThat(lancado.surchargeAmount()).isEqualByComparingTo("15.00");
        assertThat(lancado.subtotal()).isEqualByComparingTo("75.00");
        // Acréscimo é margem, não custo: o custo congelado não se mexe.
        assertThat(lancado.costPrice()).isEqualByComparingTo("12.00");
    }

    /** O registro do setup é texto opaco e não toca no preço — é o que dá casa à pinça. */
    @Test
    void addItem_notesAreStoredVerbatimAndDoNotAffectPrice() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String setup = "Narguilé grande · Com filtro · Pinça P-02";
        Comanda updated = comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, setup, null, "caixa1");

        assertThat(updated.items().get(0).notes()).isEqualTo(setup);
        assertThat(updated.items().get(0).unitPrice()).isEqualByComparingTo("60.00");
        assertThat(updated.items().get(0).surchargeAmount()).isNull();
    }

    /**
     * Nota num item comum de catálogo também vale — o narguilé e o filtro são SKU normal, e o setup
     * é registrado na primeira linha da sessão, seja ela qual for.
     */
    @Test
    void addItem_notesOnPlainCatalogLineStillPreservesCatalogPricing() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, null, false, null,
                "Pinça P-07", null, "caixa1");

        assertThat(updated.items().get(0).notes()).isEqualTo("Pinça P-07");
        assertThat(updated.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        assertThat(updated.items().get(0).mode()).isEqualTo(ConsumptionMode.NORMAL);
    }

    /** Recusa em vez de truncar: truncado, o operador não fica sabendo que perdeu o registro. */
    @Test
    void addItem_refusesNotesAboveTheColumnLimit() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(new BigDecimal("60.00")));

        String longa = "x".repeat(ComandaItem.NOTES_MAX_LENGTH + 1);
        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, longa, null, "caixa1"))
                .isInstanceOf(NotesTooLongException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(comandaRepository, never()).save(any());
    }

    /** Exatamente no limite passa — o teto é inclusivo. */
    @Test
    void addItem_acceptsNotesExactlyAtTheLimit() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String noLimite = "x".repeat(ComandaItem.NOTES_MAX_LENGTH);
        Comanda updated = comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, noLimite, null, "caixa1");

        assertThat(updated.items().get(0).notes()).hasSize(ComandaItem.NOTES_MAX_LENGTH);
    }

    /** Negativo é desconto entrando pela porta dos fundos — sem teto e sem PDV_SALE_DISCOUNT. */
    @Test
    void addItem_refusesNegativeSurcharge() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(new BigDecimal("60.00")));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, null, new BigDecimal("-5.00"), "caixa1"))
                .isInstanceOf(SurchargeInvalidException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    /** Uma linha que o cliente não paga não pode ter valor extra cobrado. Contradição, não borda. */
    @Test
    void addItem_refusesSurchargeOnCourtesyLine() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(openRoshLancado())));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(new BigDecimal("60.00")));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.TROCA, false, 77L, null, new BigDecimal("10.00"), "caixa1"))
                .isInstanceOf(SurchargeOnCourtesyException.class);
    }

    /**
     * Fora de {@code OPEN_ROSH} a diferença do sabor caro já mora no pricing da variante. A ordem
     * das checagens importa: {@code TROCA} é cortesia E não-open-rosh ao mesmo tempo, e o que o
     * operador precisa ouvir ali é "o cliente não paga esta linha" — por isso o teste acima cobra
     * {@code SURCHARGE_ON_COURTESY} e este, com uma linha cobrada, cobra o outro código.
     */
    @Test
    void addItem_refusesSurchargeOutsideOpenRosh() {
        ComandaItem primeira = ComandaItem.of(55L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("35.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.NORMAL,
                false, null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(primeira)));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-MENTA")).thenReturn(sessao(null));

        assertThatThrownBy(() -> comandaService.addItem(10L, "SESS-MENTA", BigDecimal.ONE,
                ConsumptionMode.SABOR_EXTRA, false, 55L, null, new BigDecimal("10.00"), "caixa1"))
                .isInstanceOf(SurchargeNotApplicableException.class);
    }

    /** Zero é o mesmo que não mandar: no-op não aciona recusa nem em modo comum. */
    @Test
    void addItem_zeroSurchargeIsANoOpAndPassesInAnyMode() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, null, false, null,
                null, BigDecimal.ZERO, "caixa1");

        assertThat(updated.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
    }

    /**
     * Os dois campos atravessam o fechamento. Sem isso, a tela de Vendas &gt; Pedidos não responde
     * "qual pinça saiu com aquela mesa" — pergunta que só é feita depois de a mesa fechar.
     */
    @Test
    void closeComanda_carriesNotesAndSurchargeIntoTheOrderItem() {
        ComandaItem comSetup = ComandaItem.of(88L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("75.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.OPEN_ROSH,
                false, null, "Narguilé grande · Pinça P-02", new BigDecimal("15.00"));
        Comanda comMesa = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", null, ComandaStatus.ABERTA,
                List.of(comSetup), null, "caixa1", Instant.now(), null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comMesa));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        when(cashbackUseCase.resolveApplicableRate("SESS-BLUE")).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("75.00"), null)), null, false, "caixa1");

        assertThat(order.items().get(0).notes()).isEqualTo("Narguilé grande · Pinça P-02");
        assertThat(order.items().get(0).surchargeAmount()).isEqualByComparingTo("15.00");
        // O unitPrice do pedido é o total já somado — é ele que faz o subtotal fechar.
        assertThat(order.items().get(0).unitPrice()).isEqualByComparingTo("75.00");
    }

    // ── Varredura de mesa esquecida (PDV-F013) ───────────────────────────────────────────────

    /** Comanda velha e sem item nenhum: nada saiu do estoque, então cancelar é seguro. */
    @Test
    void sweepStaleComandas_cancelsTheEmptyOnes() {
        Comanda vazia = staleComanda();
        when(comandaRepository.findStaleIdsWithNothingOwed(any(Instant.class), eq(200))).thenReturn(List.of(10L));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(vazia));

        ComandaUseCase.StaleComandaSweepResult result = comandaService.sweepStaleComandas(12, 200);

        assertThat(result.cancelled()).isEqualTo(1);
        assertThat(result.finished()).isZero();
        assertThat(result.flagged()).isZero();

        ArgumentCaptor<Comanda> saved = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ComandaStatus.CANCELADA);
        verify(comandaRepository).recordClosing(10L, "system", "Mesa vazia esquecida (varredura automática)");
        // Comanda vazia não tem o que devolver — nenhum movimento de estoque pode sair daqui.
        verifyNoInteractions(estoqueUseCase);
        // E ninguém precisa ser avisado de uma mesa vazia que o próprio sistema resolveu.
        verifyNoInteractions(notificationUseCase);
    }

    /**
     * PDV-F032 — mesa toda paga que ninguém encerrou: é o finish que faltou. Fecha no último pedido,
     * sem estoque e sem alerta — e para de travar o fechamento do caixa.
     */
    @Test
    void sweepStaleComandas_finishesTheFullyPaidOnes() {
        ComandaItem linha = linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null);
        Comanda paga = staleComanda(linha).withItemsClosedIn(400L, List.of(1L));
        when(comandaRepository.findStaleIdsWithNothingOwed(any(Instant.class), eq(200))).thenReturn(List.of(10L));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(paga));

        ComandaUseCase.StaleComandaSweepResult result = comandaService.sweepStaleComandas(12, 200);

        assertThat(result.finished()).isEqualTo(1);
        assertThat(result.cancelled()).isZero();
        ArgumentCaptor<Comanda> saved = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(saved.getValue().orderId()).isEqualTo(400L);
        verify(comandaRepository).recordClosing(10L, "system", "Mesa paga encerrada (varredura automática)");
        verifyNoInteractions(estoqueUseCase);
        verifyNoInteractions(notificationUseCase);
    }

    /**
     * <b>O caso que define a feature.</b> Mesa velha COM consumo não é cancelada: a essência já foi
     * queimada, e uma {@code ENTRADA} automática devolveria ao sistema um saldo que não existe na
     * prateleira. O sistema levanta a mão e para — cobrar, perder ou cancelar é decisão humana.
     */
    @Test
    void sweepStaleComandas_neverTouchesStockOfAComandaWithConsumption() {
        Comanda comConsumo = staleComanda(essenciaItem());
        when(comandaRepository.findOpenIdsOlderThan(any(Instant.class), eq(200))).thenReturn(List.of(10L));
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comConsumo));
        when(userRepository.findUsernamesByPermission("PDV_COMANDA_MANAGE")).thenReturn(Set.of("gerente"));

        ComandaUseCase.StaleComandaSweepResult result = comandaService.sweepStaleComandas(12, 200);

        assertThat(result.flagged()).isEqualTo(1);
        assertThat(result.cancelled()).isZero();
        // Nem devolução de estoque, nem mudança de status: a mesa fica exatamente como estava.
        verifyNoInteractions(estoqueUseCase);
        verify(comandaRepository, never()).save(any());
        // PDV-C024 — e o alerta sai sem segurar a linha da mesa.
        verify(comandaRepository, never()).findByIdForUpdate(any());
        verify(notificationUseCase).notify(eq("gerente"), eq(NotificationType.SYSTEM), anyString(), anyString());
    }

    /** Um aviso por destinatário listando todas as mesas — não um aviso por mesa. */
    @Test
    void sweepStaleComandas_sendsOneAggregatedAlertPerRecipient() {
        when(comandaRepository.findOpenIdsOlderThan(any(Instant.class), eq(200)))
                .thenReturn(List.of(10L, 11L));
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(staleComanda(essenciaItem())));
        when(comandaRepository.findById(11L)).thenReturn(Optional.of(staleComanda(essenciaItem())));
        when(userRepository.findUsernamesByPermission("PDV_COMANDA_MANAGE")).thenReturn(Set.of("gerente"));

        ComandaUseCase.StaleComandaSweepResult result = comandaService.sweepStaleComandas(12, 200);

        assertThat(result.flagged()).isEqualTo(2);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationUseCase, times(1))
                .notify(eq("gerente"), eq(NotificationType.SYSTEM), anyString(), body.capture());
        assertThat(body.getValue()).contains("2 mesas");
        // O aviso precisa dizer que o saldo NÃO foi devolvido, senão quem lê assume que foi.
        assertThat(body.getValue()).contains("continua debitado");
    }

    /** Alguém fechou a mesa entre a consulta de ids e a trava: a varredura desiste dela em silêncio. */
    @Test
    void sweepStaleComandas_skipsWhatStoppedBeingOpenBeforeTheLock() {
        Comanda jaCancelada = staleComanda().cancelled(Instant.now());
        when(comandaRepository.findStaleIdsWithNothingOwed(any(Instant.class), eq(200))).thenReturn(List.of(10L));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(jaCancelada));

        ComandaUseCase.StaleComandaSweepResult result = comandaService.sweepStaleComandas(12, 200);

        assertThat(result.cancelled()).isZero();
        assertThat(result.flagged()).isZero();
        verify(comandaRepository, never()).save(any());
    }

    /** Entre a consulta e a trava a mesa ganhou consumo: a varredura reconfere e não a encerra. */
    @Test
    void sweepStaleComandas_skipsWhatGotSomethingOwedBeforeTheLock() {
        when(comandaRepository.findStaleIdsWithNothingOwed(any(Instant.class), eq(200))).thenReturn(List.of(10L));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(staleComanda(essenciaItem())));

        ComandaUseCase.StaleComandaSweepResult result = comandaService.sweepStaleComandas(12, 200);

        assertThat(result.cancelled()).isZero();
        assertThat(result.finished()).isZero();
        verify(comandaRepository, never()).save(any());
    }

    /**
     * A varredura <b>não</b> consulta a sessão de caixa, ao contrário de {@code cancelComanda}. A
     * comanda mais presa de todas é justamente a órfã de um caixa já fechado — exigir sessão aberta
     * faria a varredura recusar o caso que ela existe para resolver.
     */
    @Test
    void sweepStaleComandas_doesNotRequireAnOpenCashRegisterSession() {
        when(comandaRepository.findStaleIdsWithNothingOwed(any(Instant.class), eq(200))).thenReturn(List.of(10L));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(staleComanda()));

        comandaService.sweepStaleComandas(12, 200);

        verifyNoInteractions(pdvService);
    }

    /** O corte vira o {@code cutoff} passado ao repositório: 12h atrás, não "agora". */
    @Test
    void sweepStaleComandas_cutsByTheConfiguredWindow() {
        comandaService.sweepStaleComandas(12, 50);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(comandaRepository).findStaleIdsWithNothingOwed(cutoff.capture(), eq(50));
        verify(comandaRepository).findOpenIdsOlderThan(cutoff.getValue(), 50);
        assertThat(cutoff.getValue()).isBefore(Instant.now().minus(11, ChronoUnit.HOURS));
        assertThat(cutoff.getValue()).isAfter(Instant.now().minus(13, ChronoUnit.HOURS));
    }

    /** Nada esquecido: nem alerta, nem escrita. */
    @Test
    void sweepStaleComandas_withNothingStaleDoesNothing() {
        ComandaUseCase.StaleComandaSweepResult result = comandaService.sweepStaleComandas(12, 200);

        assertThat(result.cancelled()).isZero();
        assertThat(result.finished()).isZero();
        assertThat(result.flagged()).isZero();
        verifyNoInteractions(notificationUseCase);
        verifyNoInteractions(estoqueUseCase);
        verify(comandaRepository, never()).save(any());
    }

    /** Comanda aberta há 13h, o cenário que a varredura de 12h alcança. */
    private Comanda staleComanda(ComandaItem... items) {
        Comanda base = abertaComanda(items);
        return Comanda.of(base.id(), base.sessionId(), base.warehouseCode(), base.tableOrCustomerLabel(),
                base.customerId(), base.status(), base.items(), base.orderId(), base.openedBy(),
                Instant.now().minus(13, ChronoUnit.HOURS), base.closedAt());
    }

    // ── Conta dividida (PDV-F017) ────────────────────────────────────────────────────────────

    /**
     * <b>O caso que define a feature.</b> Fechar parte da conta gera um pedido só com as linhas
     * escolhidas e a comanda continua ABERTA com o restante. Se ela fechasse aqui, a mesa cheia
     * perderia o consumo de quem ainda não pagou.
     */
    @Test
    void closeComanda_withItemIds_chargesOnlyThoseLinesAndKeepsTheComandaOpen() {
        Comanda comanda = abertaComanda(
                linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "ESS-B", "20.00", ConsumptionMode.NORMAL, false, null));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), eq(new BigDecimal("30.00")))).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, dinheiro("30.00"), null, false, List.of(1L), "caixa1");

        // Só a linha escolhida entrou no pedido.
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).sku()).isEqualTo("ESS-A");
        assertThat(order.netAmount()).isEqualByComparingTo("30.00");

        ArgumentCaptor<Comanda> saved = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(saved.getValue().orderId()).isNull();
        // E o que falta pagar caiu para o valor da linha que sobrou.
        assertThat(saved.getValue().runningTotal()).isEqualByComparingTo("20.00");
    }

    /**
     * PDV-F023 — o fechamento parcial nunca encerra a mesa, nem levando a última linha aberta: com a
     * sessão paga no lançamento, toda sessão seria a última. Quem encerra é o finish.
     */
    @Test
    void closeComanda_lastPartialClose_keepsTheComandaOpen() {
        Comanda comanda = abertaComanda(
                linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "ESS-B", "20.00", ConsumptionMode.NORMAL, false, null))
                .withItemsClosedIn(499L, List.of(1L));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), eq(new BigDecimal("20.00")))).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.closeComanda(10L, dinheiro("20.00"), null, false, List.of(2L), "caixa1");

        ArgumentCaptor<Comanda> saved = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(saved.getValue().orderId()).isNull();
        assertThat(saved.getValue().isFullyCharged()).isTrue();
        // PDV-F029 — mesa aberta não tem encerramento a registrar.
        verify(comandaRepository, never()).recordClosing(any(), any(), any());
    }

    /** Sem itemIds nada muda: cobra tudo que está aberto, como sempre foi. */
    @Test
    void closeComanda_withoutItemIds_stillChargesEverything() {
        Comanda comanda = abertaComanda(
                linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "ESS-B", "20.00", ConsumptionMode.NORMAL, false, null));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), eq(new BigDecimal("50.00")))).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, dinheiro("50.00"), null, false, null, "caixa1");

        assertThat(order.items()).hasSize(2);
        ArgumentCaptor<Comanda> saved = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ComandaStatus.FECHADA);
    }

    /** O desconto rateia sobre O ESCOPO, não sobre a mesa inteira. */
    @Test
    void closeComanda_partialCloseProratesTheDiscountOverTheScopeOnly() {
        Comanda comanda = abertaComanda(
                linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "ESS-B", "20.00", ConsumptionMode.NORMAL, false, null));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), eq(new BigDecimal("27.00")))).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = comandaService.closeComanda(10L, dinheiro("27.00"), new BigDecimal("3.00"),
                false, List.of(1L), "caixa1");

        // Os 3,00 caíram inteiros na única linha do escopo — não foram divididos com a linha alheia.
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).discountAmount()).isEqualByComparingTo("3.00");
        assertThat(order.netAmount()).isEqualByComparingTo("27.00");
    }

    @Test
    void closeComanda_withAnItemIdThatIsNotOpenHere_isRejected() {
        Comanda comanda = abertaComanda(linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null));
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));

        assertThatThrownBy(() -> comandaService.closeComanda(10L, dinheiro("30.00"), null, false,
                List.of(42L), "caixa1"))
                .isInstanceOf(ItemNotOpenInComandaException.class);

        verify(orderRepository, never()).save(any());
    }

    /**
     * Um OPEN_ROSH e a TROCA dele não vão para contas diferentes: separados, cada metade descreve
     * um consumo que não aconteceu, e a margem do open rosh sai partida ao meio.
     */
    @Test
    void closeComanda_selectingASessionWithoutItsTrocaIsRejected() {
        Comanda comanda = comandaComSessaoETroca();
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));

        assertThatThrownBy(() -> comandaService.closeComanda(10L, dinheiro("60.00"), null, false,
                List.of(1L), "caixa1"))
                .isInstanceOf(LinkedItemMustCloseTogetherException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void closeComanda_selectingATrocaWithoutItsSessionIsRejected() {
        Comanda comanda = comandaComSessaoETroca();
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));

        assertThatThrownBy(() -> comandaService.closeComanda(10L, dinheiro("60.00"), null, false,
                List.of(2L), "caixa1"))
                .isInstanceOf(LinkedItemMustCloseTogetherException.class);
    }

    // ── Transferir e juntar mesas (PDV-F016) ─────────────────────────────────────────────────

    @Test
    void renameComanda_changesTheLabelWithoutTouchingStock() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda renomeada = comandaService.renameComanda(10L, "Mesa 7", "caixa1");

        assertThat(renomeada.tableOrCustomerLabel()).isEqualTo("Mesa 7");
        assertThat(renomeada.items()).hasSize(1);
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void linkCustomer_setsAndClearsTheCustomerWithoutTouchingStock() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comanda));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda vinculada = comandaService.linkCustomer(10L, 42L, "caixa1");

        assertThat(vinculada.customerId()).isEqualTo(42L);
        assertThat(vinculada.items()).hasSize(1);
        assertThat(vinculada.withCustomer(null).customerId()).isNull();
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void linkCustomer_onClosedComanda_isRejected() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", null, ComandaStatus.CANCELADA,
                List.of(), null, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fechada));

        assertThatThrownBy(() -> comandaService.linkCustomer(10L, 42L, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verify(comandaRepository, never()).save(any());
    }

    /**
     * <b>Nenhum estoque se move numa junção.</b> A mercadoria não voltou para a prateleira nem saiu
     * de novo — mudou de conta. É o que separa este caminho de {@code cancelComanda}, que devolve
     * tudo por ENTRADA.
     */
    @Test
    void mergeComanda_movesTheLinesWithoutAnyStockMovement() {
        Comanda origem = abertaComanda(linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null));
        Comanda origemAposMove = abertaComanda();
        Comanda destino = Comanda.of(20L, 1L, "LOJA-01", "Mesa 9", null, ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(origem), Optional.of(origemAposMove));
        when(comandaRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(destino));
        when(comandaRepository.findById(20L)).thenReturn(Optional.of(destino));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.mergeComanda(10L, 20L, "caixa1");

        verify(comandaRepository).moveItems(10L, 20L, List.of(1L));
        // O ponto: a junção não gera movimento de estoque nenhum.
        verifyNoInteractions(estoqueUseCase);
        ArgumentCaptor<Comanda> saved = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ComandaStatus.CANCELADA);
        assertThat(saved.getValue().id()).isEqualTo(10L);
        verify(comandaRepository).recordClosing(10L, "caixa1", "Juntada à comanda #20");
    }

    /**
     * PDV-C022 — a origem salva é a RELIDA depois do move. Salvar a lida antes reinseria as linhas
     * movidas como cópias na mesa encerrada (o save não acha par para elas).
     */
    @Test
    void mergeComanda_savesTheSourceAsReadAfterTheMove_notTheStaleOne() {
        Comanda origem = abertaComanda(linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null));
        Comanda destino = Comanda.of(20L, 1L, "LOJA-01", "Mesa 9", null, ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(origem), Optional.of(abertaComanda()));
        when(comandaRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(destino));
        when(comandaRepository.findById(20L)).thenReturn(Optional.of(destino));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.mergeComanda(10L, 20L, "caixa1");

        ArgumentCaptor<Comanda> saved = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(saved.capture());
        assertThat(saved.getValue().items()).as("nenhuma linha movida volta para a origem").isEmpty();
    }

    /**
     * PDV-F031 — origem com parte já cobrada junta: vão as linhas em aberto, a cobrada fica, e a
     * origem termina FECHADA no pedido dela (é receita, e o histórico só conta FECHADA).
     */
    @Test
    void mergeComanda_withAChargedLine_movesTheOpenOnesAndClosesTheSourceOnItsOrder() {
        Comanda origem = abertaComanda(
                linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null),
                linha(2L, "ESS-B", "20.00", ConsumptionMode.NORMAL, false, null))
                .withItemsClosedIn(499L, List.of(1L));
        Comanda origemAposMove = abertaComanda(linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null))
                .withItemsClosedIn(499L, List.of(1L));
        Comanda destino = Comanda.of(20L, 1L, "LOJA-01", "Mesa 9", null, ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(origem), Optional.of(origemAposMove));
        when(comandaRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(destino));
        when(comandaRepository.findById(20L)).thenReturn(Optional.of(destino));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.mergeComanda(10L, 20L, "caixa1");

        verify(comandaRepository).moveItems(10L, 20L, List.of(2L));
        ArgumentCaptor<Comanda> saved = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(saved.getValue().orderId()).isEqualTo(499L);
        verify(comandaRepository).recordClosing(10L, "caixa1", "Juntada à comanda #20");
    }

    @Test
    void mergeComanda_isRejectedAcrossWarehousesAndOntoItself() {
        Comanda origem = abertaComanda(linha(1L, "ESS-A", "30.00", ConsumptionMode.NORMAL, false, null));
        Comanda outroDeposito = Comanda.of(20L, 1L, "LOJA-02", "Mesa 9", null, ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(origem));
        when(comandaRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(outroDeposito));

        assertThatThrownBy(() -> comandaService.mergeComanda(10L, 20L, "caixa1"))
                .isInstanceOf(ComandaMergeNotAllowedException.class);
        assertThatThrownBy(() -> comandaService.mergeComanda(10L, 10L, "caixa1"))
                .isInstanceOf(ComandaMergeNotAllowedException.class);
    }

    // ── Lata de essência aberta (EST-F027 / PDV-F018) ────────────────────────────────────────

    /** Essência vendida por sessão, com o rendimento declarado: 1 lata = 5 sessões. */
    private EstoqueUseCase.CatalogSaleInfo essenciaComLata() {
        return new EstoqueUseCase.CatalogSaleInfo("Zgy Blueberry", ESSENCIA, true, true,
                new BigDecimal("60.00"), 5, false);
    }

    /**
     * <b>O bug que a feature corrige.</b> Cada sessão baixava uma lata inteira — medido no QA de
     * 06/09/2026, {@code ESSE-ZGY-BLUEBERRY} foi de 50 para 49 numa sessão só. Agora a linha
     * consome USO, e {@code adjustStock} não é chamado.
     */
    @Test
    void addItem_comEssenciaVendidaPorSessao_consomeUsoDaLataEmVezDeBaixarUnidade() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESSE-BLUE")).thenReturn(essenciaComLata());
        when(estoqueUseCase.consumeSession("ESSE-BLUE", "LOJA-01", BigDecimal.ONE, "caixa1"))
                .thenReturn(OpenPackage.open("ESSE-BLUE", 1L, 5, "caixa1", Instant.now()).withUses(3));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "ESSE-BLUE", BigDecimal.ONE, null, false,
                null, "caixa1");

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        // O contador é carimbado na linha: é o "3 de 5" que a tela mostra sem uma segunda chamada.
        assertThat(updated.items().get(0).packageUses()).isEqualTo(3);
        assertThat(updated.items().get(0).packageSessionsPerUnit()).isEqualTo(5);
        assertThat(updated.items().get(0).consumedPackage()).isTrue();
    }

    /**
     * Produto de sessão <b>sem</b> {@code sessionsPerUnit} segue baixando unidade. É o que torna a
     * adoção da lata uma escolha por item de catálogo em vez de uma virada de chave para a casa.
     */
    @Test
    void addItem_comProdutoDeSessaoSemRendimento_mantemABaixaDireta() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("SESS-BLUE")).thenReturn(sessao(new BigDecimal("60.00")));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "SESS-BLUE", BigDecimal.ONE, null, false,
                null, "caixa1");

        verify(estoqueUseCase).adjustStock(eq("SESS-BLUE"), eq("LOJA-01"), eq(MovementType.SAIDA),
                any(), any(), eq("caixa1"));
        verify(estoqueUseCase, never()).consumeSession(any(), any(), any(), any());
        assertThat(updated.items().get(0).consumedPackage()).isFalse();
    }

    /**
     * Cancelar a mesa <b>decrementa o contador</b> e não devolve unidade: a essência foi queimada
     * e não voltou para a prateleira. Uma ENTRADA aqui inventaria saldo que fisicamente não existe.
     */
    @Test
    void cancelComanda_comLinhaDeLata_devolveUsoENaoUnidade() {
        ComandaItem deLata = ComandaItem.of(1L, "ESSE-BLUE", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Zgy Blueberry", Instant.now(), ConsumptionMode.NORMAL,
                false, null, null, null, null, 3, 5);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda(deLata)));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.cancelComanda(10L, "caixa1");

        verify(estoqueUseCase).releaseSession("ESSE-BLUE", "LOJA-01", BigDecimal.ONE);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    /** Linha que baixou unidade continua sendo devolvida por ENTRADA — os dois caminhos coexistem. */
    @Test
    void cancelComanda_comLinhaComum_continuaDevolvendoPorEntrada() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(
                abertaComanda(linha(1L, "BEB-COLA", "12.00", ConsumptionMode.NORMAL, false, null))));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.cancelComanda(10L, "caixa1");

        verify(estoqueUseCase).adjustStock(eq("BEB-COLA"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), any(), eq("caixa1"));
        verify(estoqueUseCase, never()).releaseSession(any(), any(), any());
    }

    /**
     * PDV-C020 — kit não pode ser sessão: não tem saldo próprio (explode em componentes) e não há
     * como ser "a lata" que o contador controla. O QA achou kits oferecidos como sabor, a R$ 95.
     */
    @Test
    void addItem_recusaKitEmModoDeSessao() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("KIT-8019")).thenReturn(
                new EstoqueUseCase.CatalogSaleInfo("Kit Narguileiro Deluxe", ESSENCIA, true, true,
                        new BigDecimal("95.00"), null, true));

        assertThatThrownBy(() -> comandaService.addItem(10L, "KIT-8019", BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, "caixa1"))
                .isInstanceOf(NotASessionProductException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(estoqueUseCase, never()).consumeSession(any(), any(), any(), any());
    }

    // ── PDV-F019 — kit montável ──────────────────────────────────────────────────────────────

    private static final Pricing BAG = Pricing.of(new BigDecimal("20.00"), null, new BigDecimal("40.00"));
    private static final Pricing SEDA = Pricing.of(new BigDecimal("2.00"), null, new BigDecimal("10.00"));

    private static final KitTemplate KIT_MAHAL = new KitTemplate(7L, "Kit Mahal", null, null, new BigDecimal("10"),
            true, true, true, List.of(new KitTemplateStep(10L, "Bag", 0, 1L, true, 1),
                    new KitTemplateStep(20L, "Seda", 1, 2L, true, 1)));

    private static final KitSelection SELECAO = new KitSelection(7L, List.of(
            new KitSelection.Pick(10L, "BAG-01"), new KitSelection.Pick(20L, "SEDA-01")));

    private static KitQuote cotacao() {
        return new KitQuote(KIT_MAHAL, List.of(
                new KitQuote.Line(10L, "Bag", "BAG-01", "Bag", new BigDecimal("40.00"), new BigDecimal("4.00")),
                new KitQuote.Line(20L, "Seda", "SEDA-01", "Seda", new BigDecimal("10.00"), new BigDecimal("1.00"))),
                new BigDecimal("50.00"), new BigDecimal("5.00"), new BigDecimal("45.00"));
    }

    private static Comanda comandaComKit() {
        return abertaComandaStatic(
                ComandaItem.of(1L, "BAG-01", BigDecimal.ONE, new BigDecimal("40.00"), new BigDecimal("20.00"), "Bag",
                        Instant.now(), null, false, null, null, null, null, null, null, "b-1", 7L,
                        new BigDecimal("4.00")),
                ComandaItem.of(2L, "SEDA-01", BigDecimal.ONE, new BigDecimal("10.00"), new BigDecimal("2.00"), "Seda",
                        Instant.now(), null, false, null, null, null, null, null, null, "b-1", 7L,
                        new BigDecimal("1.00")),
                ComandaItem.of(3L, "BEB-COLA", BigDecimal.ONE, new BigDecimal("50.00"), new BigDecimal("10.00"),
                        "Refrigerante", Instant.now()));
    }

    private static Comanda abertaComandaStatic(ComandaItem... items) {
        Comanda comanda = Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1");
        for (ComandaItem item : items) {
            comanda = comanda.withAddedItem(item);
        }
        return Comanda.of(10L, comanda.sessionId(), comanda.warehouseCode(), comanda.tableOrCustomerLabel(),
                comanda.customerId(), comanda.status(), comanda.items(), comanda.orderId(), comanda.openedBy(),
                comanda.openedAt(), comanda.closedAt());
    }

    @Test
    void addKit_writesOneLinePerPickWithSharedBundleAndDebitsEach() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(abertaComanda()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(kitBuilderUseCase.quote(SELECAO, KitChannel.PDV)).thenReturn(cotacao());
        when(estoqueUseCase.resolveSaleInfo("BAG-01")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Bag", BAG));
        when(estoqueUseCase.resolveSaleInfo("SEDA-01")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Seda", SEDA));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addKit(10L, SELECAO, "caixa1");

        assertThat(updated.items()).extracting(ComandaItem::sku, ComandaItem::kitDiscountAmount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("BAG-01", new BigDecimal("4.00")),
                        org.assertj.core.groups.Tuple.tuple("SEDA-01", new BigDecimal("1.00")));
        assertThat(updated.items().get(0).kitBundleId()).isNotNull().isEqualTo(updated.items().get(1).kitBundleId());
        // Preço cheio na linha; o total da mesa já é líquido do kit.
        assertThat(updated.items().get(0).unitPrice()).isEqualByComparingTo("40.00");
        assertThat(updated.runningTotal()).isEqualByComparingTo("45.00");
        verify(estoqueUseCase).adjustStock(eq("BAG-01"), eq("LOJA-01"), eq(MovementType.SAIDA), eq(BigDecimal.ONE),
                any(), eq("caixa1"));
        verify(estoqueUseCase).adjustStock(eq("SEDA-01"), eq("LOJA-01"), eq(MovementType.SAIDA), eq(BigDecimal.ONE),
                any(), eq("caixa1"));
    }

    @Test
    void removeItem_refusesSingleLineOfKit() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaComKit()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.removeItem(10L, 1L, "caixa1"))
                .isInstanceOf(KitItemRemovalNotAllowedException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void removeKit_removesWholeBundleAndReturnsStockOfEachLine() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaComKit()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.removeKit(10L, "b-1", "caixa1");

        assertThat(updated.items()).extracting(ComandaItem::sku).containsExactly("BEB-COLA");
        verify(estoqueUseCase).adjustStock(eq("BAG-01"), eq("LOJA-01"), eq(MovementType.ENTRADA), eq(BigDecimal.ONE),
                any(), eq("caixa1"));
        verify(estoqueUseCase).adjustStock(eq("SEDA-01"), eq("LOJA-01"), eq(MovementType.ENTRADA), eq(BigDecimal.ONE),
                any(), eq("caixa1"));
    }

    /**
     * O desconto do kit vai para o item junto com o desconto de conta, e o de conta é rateado
     * sobre o líquido do kit: 100 cheio − 5 do kit = 95; 9,50 de desconto de conta sai 10% de cada.
     */
    @Test
    void closeComanda_addsKitDiscountToLineAndExemptsItFromLimit() {
        givenCloseablePara(comandaComKit());

        Order order = comandaService.closeComanda(10L, dinheiro("85.50"), new BigDecimal("9.50"), false, "caixa1");

        assertThat(order.items()).extracting(i -> i.sku(), i -> i.discountAmount())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("BAG-01", new BigDecimal("7.60")),
                        org.assertj.core.groups.Tuple.tuple("SEDA-01", new BigDecimal("1.90")),
                        org.assertj.core.groups.Tuple.tuple("BEB-COLA", new BigDecimal("5.00")));
        assertThat(order.discountAmount()).isEqualByComparingTo("14.50");
        assertThat(order.netAmount()).isEqualByComparingTo("85.50");
        verify(pdvService).requireDiscountWithinLimit(any(Order.class), eq(new BigDecimal("5.00")));
        verify(pdvService, never()).requireDiscountWithinLimit(any(Order.class));
    }

    @Test
    void removeKit_throwsWhenBundleIsNotInComanda() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(comandaComKit()));
        when(pdvService.requireOpenSession(1L)).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.removeKit(10L, "nao-existe", "caixa1"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.pdv.ComandaItemNotFoundException.class);
    }

    // ── Histórico e indicadores de mesas (PDV-F029) ──────────────────────────────────────────

    private static Comanda fechadaEm(Long id, String label, String openedBy, Instant openedAt, long minutes,
            ComandaItem... items) {
        return Comanda.of(id, 1L, "LOJA-01", label, ComandaStatus.FECHADA, List.of(items), 900L + id, openedBy,
                openedAt, openedAt.plus(minutes, ChronoUnit.MINUTES));
    }

    private static OrderItem itemPedido(String sku, String unitPrice, String costPrice, int quantity,
            ConsumptionMode mode, boolean courtesy) {
        return OrderItem.of(null, sku, BigDecimal.valueOf(quantity), new BigDecimal(unitPrice),
                new BigDecimal(costPrice), BigDecimal.ZERO, null, sku, mode, courtesy);
    }

    /** Pedido MESA com o bruto somado dos itens; o líquido é bruto − desconto. */
    private static Order pedidoMesa(Long id, Long comandaId, OrderStatus status, String discount, String fee,
            OrderItem... items) {
        BigDecimal gross = java.util.Arrays.stream(items).map(OrderItem::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal desconto = new BigDecimal(discount);
        Instant now = Instant.now();
        return Order.of(id, "00000" + id, SalesChannel.MESA, status, null, 1L, "LOJA-01", List.of(items), gross,
                desconto, BigDecimal.ZERO, gross.subtract(desconto), null, null, now, now, now, null,
                status == OrderStatus.REEMBOLSADO ? now : null, null, null, null, null, 0L, comandaId, "Mesa 4",
                new BigDecimal(fee));
    }

    /**
     * Os totais da mesa saem dos pedidos que ela gerou: o reembolsado fica fora de tudo, a
     * cortesia é medida pelo custo (o preço dela é zero por construção) e as sessões vêm das
     * linhas SESSAO da comanda.
     */
    @Test
    void listHistory_derivesTheTotalsFromTheOrders() {
        Instant aberta = Instant.parse("2026-09-30T22:00:00Z");
        Comanda mesa = fechadaEm(10L, "Mesa 4", "caixa1", aberta, 90,
                linha(1L, "SESS-BLUE", "70.00", ConsumptionMode.SESSAO, false, null),
                linha(2L, "BEB-COLA", "0.00", ConsumptionMode.NORMAL, true, null));
        ComandaHistoryFilter filter = new ComandaHistoryFilter(null, null, null, null, null, null, null, null);
        when(comandaRepository.findHistory(filter, 0, 50)).thenReturn(
                new PageResult<>(List.of(new ClosedComanda(mesa, "caixa2", null)), 0, 50, 1, 1));
        when(orderRepository.findByComandaIds(List.of(10L))).thenReturn(List.of(
                pedidoMesa(500L, 10L, OrderStatus.CONCLUIDO, "5.00", "7.00",
                        itemPedido("SESS-BLUE", "70.00", "20.00", 1, ConsumptionMode.SESSAO, false),
                        itemPedido("BEB-COLA", "0.00", "2.50", 2, ConsumptionMode.NORMAL, true)),
                pedidoMesa(501L, 10L, OrderStatus.REEMBOLSADO, "0.00", "3.00",
                        itemPedido("BEB-COLA", "30.00", "2.50", 1, ConsumptionMode.NORMAL, false))));

        PageResult<ComandaUseCase.ComandaHistoryEntry> page = comandaService.listHistory(filter, 0, 50);

        assertThat(page.totalElements()).isEqualTo(1);
        ComandaUseCase.ComandaHistoryEntry entry = page.content().get(0);
        assertThat(entry.closedBy()).isEqualTo("caixa2");
        assertThat(entry.durationMinutes()).isEqualTo(90L);
        // O reembolsado aparece na lista de pedidos da mesa, mas não soma em nada.
        assertThat(entry.orders()).extracting(Order::id).containsExactly(500L, 501L);
        assertThat(entry.totalPaid()).isEqualByComparingTo("72.00");
        assertThat(entry.serviceFeeTotal()).isEqualByComparingTo("7.00");
        assertThat(entry.discountTotal()).isEqualByComparingTo("5.00");
        assertThat(entry.courtesyTotal()).isEqualByComparingTo("5.00");
        assertThat(entry.sessionsCount()).isEqualTo(1);
        // A listagem não busca pagamento: isso é do detalhe.
        assertThat(entry.paymentsByOrder()).isEmpty();
        verifyNoInteractions(orderPaymentRepository);
    }

    @Test
    void listHistory_emptyPage_doesNotQueryOrders() {
        ComandaHistoryFilter filter = new ComandaHistoryFilter(null, null, null, null, null, null, null, null);
        when(comandaRepository.findHistory(filter, 0, 50)).thenReturn(new PageResult<>(List.of(), 0, 50, 0, 0));

        assertThat(comandaService.listHistory(filter, 0, 50).content()).isEmpty();
        verifyNoInteractions(orderRepository);
    }

    @Test
    void getHistoryEntry_bringsThePaymentsOfEachOrder() {
        Comanda mesa = fechadaEm(10L, "Mesa 4", "caixa1", Instant.now().minus(1, ChronoUnit.HOURS), 30,
                linha(1L, "BEB-COLA", "30.00", ConsumptionMode.NORMAL, false, null));
        when(comandaRepository.findWithClosing(10L)).thenReturn(Optional.of(new ClosedComanda(mesa, "caixa1", null)));
        when(orderRepository.findByComandaIds(List.of(10L))).thenReturn(List.of(
                pedidoMesa(500L, 10L, OrderStatus.CONCLUIDO, "0.00", "0.00",
                        itemPedido("BEB-COLA", "30.00", "2.50", 1, ConsumptionMode.NORMAL, false))));
        when(orderPaymentRepository.findByOrderId(500L)).thenReturn(List.of());

        ComandaUseCase.ComandaHistoryEntry entry = comandaService.getHistoryEntry(10L);

        assertThat(entry.paymentsByOrder()).containsOnlyKeys(500L);
        assertThat(entry.totalPaid()).isEqualByComparingTo("30.00");
    }

    /** Comanda ABERTA também tem detalhe: sem pedido e sem duração. */
    @Test
    void getHistoryEntry_ofAnOpenComanda_hasNoDuration() {
        when(comandaRepository.findWithClosing(10L))
                .thenReturn(Optional.of(new ClosedComanda(abertaComanda(), null, null)));
        when(orderRepository.findByComandaIds(List.of(10L))).thenReturn(List.of());

        ComandaUseCase.ComandaHistoryEntry entry = comandaService.getHistoryEntry(10L);

        assertThat(entry.durationMinutes()).isNull();
        assertThat(entry.orders()).isEmpty();
        assertThat(entry.totalPaid()).isEqualByComparingTo("0");
    }

    @Test
    void getHistoryEntry_notFound() {
        when(comandaRepository.findWithClosing(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comandaService.getHistoryEntry(999L)).isInstanceOf(ComandaNotFoundException.class);
    }

    /**
     * <b>O caso que define os indicadores.</b> Sem cadastro de mesa, "Mesa 4" e " mesa 4 " são a
     * mesma; a hora é a da abertura no relógio da loja (23:30Z é 20:30 em São Paulo), não em UTC.
     */
    @Test
    void analytics_groupsByNormalizedTableAndOpeningHourInSaoPaulo() {
        Instant from = Instant.parse("2026-09-30T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T23:59:59Z");
        Comanda a = fechadaEm(10L, "Mesa 4", "ana", Instant.parse("2026-09-30T23:30:00Z"), 60,
                linha(1L, "SESS-BLUE", "100.00", ConsumptionMode.SESSAO, false, null));
        Comanda b = fechadaEm(11L, " mesa 4 ", "bia", Instant.parse("2026-10-01T00:10:00Z"), 120,
                linha(2L, "BEB-COLA", "50.00", ConsumptionMode.NORMAL, false, null));
        Comanda c = fechadaEm(12L, "Mesa 9", "ana", Instant.parse("2026-10-01T00:40:00Z"), 30,
                linha(3L, "BEB-COLA", "40.00", ConsumptionMode.NORMAL, false, null));
        when(comandaRepository.findClosedBetween(from, to, "LOJA-01")).thenReturn(List.of(
                new ClosedComanda(a, "ana", null), new ClosedComanda(b, "bia", null),
                new ClosedComanda(c, "ana", null)));
        when(orderRepository.findByComandaIds(List.of(10L, 11L, 12L))).thenReturn(List.of(
                pedidoMesa(500L, 10L, OrderStatus.CONCLUIDO, "0.00", "10.00",
                        itemPedido("SESS-BLUE", "100.00", "30.00", 1, ConsumptionMode.SESSAO, false)),
                pedidoMesa(501L, 11L, OrderStatus.CONCLUIDO, "0.00", "0.00",
                        itemPedido("BEB-COLA", "50.00", "10.00", 1, ConsumptionMode.NORMAL, false)),
                pedidoMesa(502L, 12L, OrderStatus.REEMBOLSADO, "0.00", "0.00",
                        itemPedido("BEB-COLA", "40.00", "10.00", 1, ConsumptionMode.NORMAL, false))));

        ComandaUseCase.ComandaAnalytics result = comandaService.analytics(from, to, "LOJA-01");

        assertThat(result.mesas()).isEqualTo(3);
        assertThat(result.receitaTotal()).isEqualByComparingTo("160.00");
        assertThat(result.ticketMedio()).isEqualByComparingTo("53.33");
        assertThat(result.permanenciaMediaMin()).isEqualTo(70L);
        assertThat(result.taxaServicoTotal()).isEqualByComparingTo("10.00");
        assertThat(result.sessoesNarguile().quantidade()).isEqualTo(1);
        assertThat(result.sessoesNarguile().receita()).isEqualByComparingTo("100.00");

        assertThat(result.porMesa()).hasSize(2);
        ComandaUseCase.PorMesa mesa4 = result.porMesa().get(0);
        assertThat(mesa4.tableLabel()).isEqualTo("Mesa 4");
        assertThat(mesa4.mesas()).isEqualTo(2);
        assertThat(mesa4.receita()).isEqualByComparingTo("160.00");
        assertThat(mesa4.permanenciaMediaMin()).isEqualTo(90L);
        assertThat(result.porMesa().get(1).tableLabel()).isEqualTo("Mesa 9");
        assertThat(result.porMesa().get(1).receita()).isEqualByComparingTo("0");

        assertThat(result.porHora()).extracting(ComandaUseCase.PorHora::hora, ComandaUseCase.PorHora::mesasAbertas)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(20, 1), org.assertj.core.groups.Tuple.tuple(21, 2));
        assertThat(result.porHora().get(0).receita()).isEqualByComparingTo("110.00");

        assertThat(result.porAtendente()).extracting(ComandaUseCase.PorAtendente::username,
                        ComandaUseCase.PorAtendente::mesas)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ana", 2), org.assertj.core.groups.Tuple.tuple("bia", 1));
    }

    @Test
    void analytics_withoutAnyTable_returnsZeros() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plus(366, ChronoUnit.DAYS);
        when(comandaRepository.findClosedBetween(from, to, null)).thenReturn(List.of());

        ComandaUseCase.ComandaAnalytics result = comandaService.analytics(from, to, null);

        assertThat(result.mesas()).isZero();
        assertThat(result.ticketMedio()).isEqualByComparingTo("0");
        assertThat(result.porMesa()).isEmpty();
        verifyNoInteractions(orderRepository);
    }

    @Test
    void analytics_refusesAnInvertedPeriod() {
        Instant to = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> comandaService.analytics(to.plusSeconds(1), to, null))
                .isInstanceOf(InvalidReportPeriodException.class);
        verifyNoInteractions(comandaRepository);
    }

    @Test
    void analytics_refusesMoreThan366Days() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> comandaService.analytics(from, from.plus(367, ChronoUnit.DAYS), null))
                .isInstanceOf(InvalidReportPeriodException.class);
        verifyNoInteractions(comandaRepository);
    }

    // ── PDV-F035 / PDV-F036 — linha do tempo e "comprou na loja?" ───────────────────────────

    private static ComandaItem sessaoComHorarios(Long id, Instant lancada, SessionProgress progress, Long closedIn) {
        return ComandaItem.of(id, "SESS-2", BigDecimal.ONE, new BigDecimal("30.00"), null, "Sessão Premium",
                lancada, ConsumptionMode.SESSAO, false, null, "Zomo Uva", null, closedIn, null, null, null, null,
                null, progress);
    }

    /**
     * O detalhe traz a linha do tempo de cada sessão, com o pagamento vindo do pedido que a cobrou,
     * e os dois intervalos da mesa: abertura → 1ª sessão e último recolhimento → encerramento.
     */
    @Test
    void getHistoryEntry_montaALinhaDoTempoDasSessoes() {
        Instant aberta = Instant.parse("2026-10-02T22:00:00Z");
        Instant lancada = aberta.plus(10, ChronoUnit.MINUTES);
        Instant inicio = lancada.plus(5, ChronoUnit.MINUTES);
        SessionProgress p = new SessionProgress(SessionStatus.RECOLHIDO, inicio, inicio.plus(10, ChronoUnit.MINUTES),
                inicio.plus(70, ChronoUnit.MINUTES));
        Comanda mesa = fechadaEm(10L, "Mesa 4", "caixa1", aberta, 100,
                sessaoComHorarios(1L, lancada, p, 500L),
                linha(2L, "BEB-COLA", "12.00", ConsumptionMode.NORMAL, false, null));
        when(comandaRepository.findWithClosing(10L)).thenReturn(Optional.of(
                new ClosedComanda(mesa, "caixa1", null, new StorePurchase(true, "caixa1", aberta))));
        Order pedido = pedidoMesa(500L, 10L, OrderStatus.CONCLUIDO, "0.00", "0.00",
                itemPedido("SESS-2", "30.00", "0.00", 1, ConsumptionMode.SESSAO, false));
        when(orderRepository.findByComandaIds(List.of(10L))).thenReturn(List.of(pedido));
        when(orderPaymentRepository.findByOrderId(500L)).thenReturn(List.of());

        ComandaUseCase.ComandaHistoryEntry entry = comandaService.getHistoryEntry(10L);

        assertThat(entry.sessions()).singleElement().satisfies(t -> {
            assertThat(t.itemId()).isEqualTo(1L);
            assertThat(t.pagaEm()).isEqualTo(pedido.concludedAt());
            assertThat(t.esperaMin()).isEqualTo(5L);
            assertThat(t.preparoMin()).isEqualTo(10L);
            assertThat(t.naMesaMin()).isEqualTo(60L);
            assertThat(t.totalMin()).isEqualTo(75L);
        });
        assertThat(entry.aberturaAtePrimeiraSessaoMin()).isEqualTo(10L);
        // Recolhida 85 min depois da abertura, mesa encerrada aos 100.
        assertThat(entry.ultimoRecolhimentoAteEncerramentoMin()).isEqualTo(15L);
        assertThat(entry.storePurchase().bought()).isTrue();
    }

    /** A listagem continua leve: sem linha do tempo, mas com a resposta da loja. */
    @Test
    void listHistory_naoMontaALinhaDoTempo_masTrazACompraNaLoja() {
        Comanda mesa = fechadaEm(10L, "Mesa 4", "caixa1", Instant.now().minus(2, ChronoUnit.HOURS), 60,
                sessaoComHorarios(1L, Instant.now().minus(2, ChronoUnit.HOURS), recolhidaHa(), 500L));
        ComandaHistoryFilter filter = new ComandaHistoryFilter(null, null, null, null, null, null, null, null, true);
        when(comandaRepository.findHistory(filter, 0, 50)).thenReturn(new PageResult<>(List.of(
                new ClosedComanda(mesa, "caixa1", null, new StorePurchase(true, "caixa1", Instant.now()))),
                0, 50, 1, 1));
        when(orderRepository.findByComandaIds(List.of(10L))).thenReturn(List.of());

        ComandaUseCase.ComandaHistoryEntry entry = comandaService.listHistory(filter, 0, 50).content().get(0);

        assertThat(entry.sessions()).isEmpty();
        assertThat(entry.storePurchase().bought()).isTrue();
    }

    private static SessionProgress recolhidaHa() {
        Instant t = Instant.now().minus(90, ChronoUnit.MINUTES);
        return new SessionProgress(SessionStatus.RECOLHIDO, t, t.plus(5, ChronoUnit.MINUTES),
                t.plus(65, ChronoUnit.MINUTES));
    }

    /**
     * A conversão conta só mesas com sessão, e a taxa só sobre as respondidas: "não respondido" não é
     * "não comprou". As médias de fase ignoram a espera da sessão paga no final, que não espera.
     */
    @Test
    void analytics_conversaoNaLoja_eMediasDeFase() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-03T00:00:00Z");
        Instant abre = Instant.parse("2026-10-02T22:00:00Z");
        // Paga antes: espera 10, preparo 6, na mesa 60.
        SessionProgress antes = new SessionProgress(SessionStatus.RECOLHIDO, abre.plus(10, ChronoUnit.MINUTES),
                abre.plus(16, ChronoUnit.MINUTES), abre.plus(76, ChronoUnit.MINUTES));
        // Paga no final: espera 0 (fora da média), preparo 4, na mesa 40.
        SessionProgress depois = new SessionProgress(SessionStatus.RECOLHIDO, abre, abre.plus(4, ChronoUnit.MINUTES),
                abre.plus(44, ChronoUnit.MINUTES), true);
        Comanda comprou = fechadaEm(10L, "Mesa 1", "ana", abre, 90, sessaoComHorarios(1L, abre, antes, 500L));
        Comanda naoComprou = fechadaEm(11L, "Mesa 2", "ana", abre, 90, sessaoComHorarios(2L, abre, depois, 501L));
        Comanda semResposta = fechadaEm(12L, "Mesa 3", "ana", abre, 90, sessaoComHorarios(3L, abre, antes, 502L));
        Comanda semSessao = fechadaEm(13L, "Mesa 4", "ana", abre, 30,
                linha(4L, "BEB-COLA", "12.00", ConsumptionMode.NORMAL, false, null));
        when(comandaRepository.findClosedBetween(from, to, null)).thenReturn(List.of(
                new ClosedComanda(comprou, "ana", null, new StorePurchase(true, "ana", abre)),
                new ClosedComanda(naoComprou, "ana", null, new StorePurchase(false, "ana", abre)),
                new ClosedComanda(semResposta, "ana", null),
                new ClosedComanda(semSessao, "ana", null, new StorePurchase(true, "ana", abre))));
        when(orderRepository.findByComandaIds(List.of(10L, 11L, 12L, 13L))).thenReturn(List.of());

        ComandaUseCase.ComandaAnalytics result = comandaService.analytics(from, to, null);

        ComandaUseCase.CompraNaLoja loja = result.compraNaLoja();
        assertThat(loja.mesasComSessao()).isEqualTo(3);
        assertThat(loja.respondidas()).isEqualTo(2);
        assertThat(loja.compraram()).isEqualTo(1);
        assertThat(loja.taxaConversao()).isEqualByComparingTo("50.00");
        ComandaUseCase.SessoesNarguile s = result.sessoesNarguile();
        assertThat(s.esperaMediaMin()).isEqualTo(10L);
        assertThat(s.preparoMedioMin()).isEqualTo(5L);
        assertThat(s.naMesaMediaMin()).isEqualTo(53L);
        assertThat(s.pagasNoFinal()).isEqualTo(1);
        assertThat(s.desistidas()).isZero();
    }

    @Test
    void analytics_semNenhumaResposta_taxaNula() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-03T00:00:00Z");
        Comanda mesa = fechadaEm(10L, "Mesa 1", "ana", Instant.parse("2026-10-02T22:00:00Z"), 60,
                linha(1L, "SESS-2", "30.00", ConsumptionMode.SESSAO, false, null));
        when(comandaRepository.findClosedBetween(from, to, null)).thenReturn(List.of(new ClosedComanda(mesa, "ana", null)));
        when(orderRepository.findByComandaIds(List.of(10L))).thenReturn(List.of());

        ComandaUseCase.CompraNaLoja loja = comandaService.analytics(from, to, null).compraNaLoja();

        assertThat(loja.mesasComSessao()).isEqualTo(1);
        assertThat(loja.respondidas()).isZero();
        assertThat(loja.taxaConversao()).isNull();
    }

    @Test
    void recordStorePurchase_gravaComQuemRespondeu() {
        when(comandaRepository.recordStorePurchase(eq(10L), eq(true), eq("caixa1"), any())).thenReturn(true);

        comandaService.recordStorePurchase(10L, true, "caixa1");

        verify(comandaRepository).recordStorePurchase(eq(10L), eq(true), eq("caixa1"), any());
    }

    @Test
    void recordStorePurchase_comandaInexistente() {
        when(comandaRepository.recordStorePurchase(eq(999L), eq(false), eq("caixa1"), any())).thenReturn(false);

        assertThatThrownBy(() -> comandaService.recordStorePurchase(999L, false, "caixa1"))
                .isInstanceOf(ComandaNotFoundException.class);
    }
}
