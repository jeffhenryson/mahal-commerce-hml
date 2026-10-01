package com.cernecommerce.core.service;

import java.time.ZoneOffset;
import java.time.Clock;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionStaleException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.pagamento.ChangeNotSupportedException;
import com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentExceedsOrderTotalException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.exception.pedido.ItemDiscountExceedsGrossException;
import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.exception.pdv.InvalidPaymentChannelException;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentTotal;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionHasOpenComandasException;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdvServiceTest {

    /** Carvão do exemplo do plano: custo 18,00, venda 22,00. */
    private static final Pricing CARVAO = Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00"));

    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("10");

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CashbackUseCase cashbackUseCase;
    // PDV-C005 — o fechamento consulta as mesas abertas da sessão antes de deixar fechar.
    @Mock ComandaRepository comandaRepository;

    PdvService pdvService;

    @BeforeEach
    void setUp() {
        pdvService = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository,
                MAX_DISCOUNT_PERCENT);
    }

    /** Uma linha de pagamento em dinheiro, exata — o caso comum dos testes que não testam pagamento. */
    private static List<PaymentCommand> cash(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    private CashRegisterSession openSession() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
    }

    private CashRegisterSession closedSession() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                Instant.now(), "gerente", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                CashRegisterSession.Status.CLOSED);
    }

    private void givenOpenSessionAndPersistence() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        // Simula a atribuição de id que o JPA (GenerationType.IDENTITY) faz de verdade — sem isso,
        // saved.id() fica null e o captura-pagamento-depois-de-salvar (PDV-F006) quebra aqui, não
        // em produção: OrderPayment exige orderId.
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order arg = inv.getArgument(0);
            // Forma canônica de Order.of — uma sobrecarga menor descartaria campos em silêncio
            // (serviceFeeAmount, delivery...) e o teste passaria sem enxergar o que foi gravado.
            return arg.id() != null ? arg : Order.of(100L, arg.orderNumber(), arg.channel(), arg.status(),
                    arg.customerId(), arg.sessionId(), arg.warehouseCode(), arg.items(), arg.grossAmount(),
                    arg.discountAmount(), arg.cashbackRedeemed(), arg.netAmount(), arg.changeAmount(),
                    arg.cancelReason(), arg.createdAt(), arg.paidAt(), arg.concludedAt(), arg.cancelledAt(),
                    arg.refundedAt(), arg.reservedAt(), arg.separatedAt(), arg.shippedAt(), arg.deliveredAt(),
                    arg.version(), arg.comandaId(), arg.tableLabel(), arg.serviceFeeAmount(), arg.delivery());
        });
    }

    private static SaleItemCommand twoCharcoals(BigDecimal discount) {
        return new SaleItemCommand("CARV-001", new BigDecimal("2.000"), discount);
    }

    // ── PDV-F001: abertura e fechamento ──────────────────────────────────────────────────────

    @Test
    void openSession_validatesTheWarehouseBeforeStampingItOnTheSession() {
        when(cashRegisterRepository.findOpenByOperator("caixa1")).thenReturn(Optional.empty());
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashRegisterSession session = pdvService.openSession("caixa1", new BigDecimal("200.00"), "LOJA-01");

        assertThat(session.operator()).isEqualTo("caixa1");
        assertThat(session.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(session.isOpen()).isTrue();
        // Descobrir que o depósito não existe só na primeira venda seria descobrir tarde demais.
        verify(estoqueUseCase).getWarehouseByCode("LOJA-01");
    }

    @Test
    void openSession_refusesASecondOpenSessionForTheSameOperator() {
        when(cashRegisterRepository.findOpenByOperator("caixa1")).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.openSession("caixa1", BigDecimal.TEN, "LOJA-01"))
                .isInstanceOf(CashRegisterSessionAlreadyOpenException.class);

        verify(cashRegisterRepository, never()).save(any());
    }

    @Test
    void getCurrentSession_throwsWhenTheOperatorHasNoOpenRegister() {
        when(cashRegisterRepository.findOpenByOperator("caixa1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.getCurrentSession("caixa1"))
                .isInstanceOf(NoOpenCashRegisterSessionException.class);
    }

    /**
     * PDV-C017/C018 — nenhuma saída em espécie na sessão: nenhum troco devolvido, nenhum estorno.
     * Precisa ser explícito porque o mock devolve {@code null} para {@code BigDecimal}, enquanto o
     * contrato dos dois ports é "zero, nunca null".
     */
    private void givenNoCashOutflows() {
        when(orderPaymentRepository.sumRefundedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumChangeAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void closeSession_computesExpectedFromOpeningCashSalesAndMovements() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        // abertura 10,00 + vendas em DINHEIRO 500,00 − sangria líquida 150,00 = 360,00
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(new BigDecimal("500.00"));
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(new BigDecimal("-150.00"));
        givenNoCashOutflows();
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashRegisterSession closed = pdvService.closeSession(1L, new BigDecimal("355.00"), "gerente");

        assertThat(closed.expectedAmount()).isEqualByComparingTo("360.00");
        assertThat(closed.countedAmount()).isEqualByComparingTo("355.00");
        assertThat(closed.differenceAmount()).isEqualByComparingTo("-5.00");
        assertThat(closed.diverges()).isTrue();
        assertThat(closed.status()).isEqualTo(CashRegisterSession.Status.CLOSED);
    }

    /** Vendas no débito/crédito/PIX não entram no esperado da gaveta — só se conferem contra a adquirente. */
    @Test
    void closeSession_ignoresNonCashPaymentsInTheExpectedAmount() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(BigDecimal.ZERO);
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        givenNoCashOutflows();
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Só a abertura conta: nenhuma venda em dinheiro na sessão, mesmo que tenha vendido em cartão.
        CashRegisterSession closed = pdvService.closeSession(1L, BigDecimal.TEN, "gerente");

        assertThat(closed.expectedAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void closeSession_doesNotRequireBeingTheOwner() {
        // A conferência costuma ser do gerente — daí PDV_SESSION_CLOSE separada de PDV_SESSION_MANAGE.
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(BigDecimal.ZERO);
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        givenNoCashOutflows();
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(pdvService.closeSession(1L, BigDecimal.TEN, "gerente").closedBy()).isEqualTo("gerente");
    }

    private void givenClosableSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(BigDecimal.ZERO);
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        givenNoCashOutflows();
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void closeSession_adminClosesAnotherOperatorsSessionWithNotes() {
        givenClosableSession();

        CashRegisterSession closed = pdvService.closeSession(1L, BigDecimal.TEN,
                "  Operador saiu sem fechar  ", "admin", true);

        assertThat(closed.closedBy()).isEqualTo("admin");
        assertThat(closed.operator()).isEqualTo("caixa1");
        assertThat(closed.closingNotes()).isEqualTo("Operador saiu sem fechar");
    }

    @Test
    void closeSession_ownerClosesOwnSessionWithoutPrivilege() {
        givenClosableSession();

        CashRegisterSession closed = pdvService.closeSession(1L, BigDecimal.TEN, null, "caixa1", false);

        assertThat(closed.status()).isEqualTo(CashRegisterSession.Status.CLOSED);
        assertThat(closed.closingNotes()).isNull();
    }

    @Test
    void closeSession_nonPrivilegedCannotCloseAnotherOperatorsSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.closeSession(1L, BigDecimal.TEN, null, "atendente2", false))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);
        verify(cashRegisterRepository, never()).save(any());
    }

    // ── PDV-C017/C018 — o esperado conta o que SAIU da gaveta ────────────────────────────────

    /**
     * PDV-C017 — o troco sai do esperado.
     *
     * <p>{@code order_payment.amount} em {@code DINHEIRO} é o valor <b>entregue</b> pelo cliente,
     * não o retido: é assim que {@code validatePaymentsAndComputeChange} deriva o troco, e é o
     * valor inteiro que vira a linha de pagamento. Sem subtrair o troco, toda venda em dinheiro
     * com nota quebrada inflava o esperado e o operador fechava o turno acusando uma falta que era
     * só aritmética.</p>
     */
    @Test
    void closeSession_subtractsTheChangeGivenBackFromTheExpectedAmount() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        // abertura 10,00 + entregue em dinheiro 100,00 − troco devolvido 7,00 = 103,00
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(new BigDecimal("100.00"));
        when(orderPaymentRepository.sumRefundedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumChangeAmountBySessionId(1L)).thenReturn(new BigDecimal("7.00"));
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashRegisterSession closed = pdvService.closeSession(1L, new BigDecimal("103.00"), "gerente");

        assertThat(closed.expectedAmount()).isEqualByComparingTo("103.00");
        assertThat(closed.diverges()).isFalse();
    }

    /**
     * PDV-C018 — a cédula estornada sai do esperado.
     *
     * <p>O ledger é append-only de propósito: {@code OrderPayment.refunded} grava uma linha nova e
     * deixa a {@code CAPTURED} original de pé, que é o desenho certo para o histórico. A
     * consequência é que a soma de capturados descreve tudo que entrou e nada do que voltou — daí
     * a subtração precisar de consulta própria em vez de sair de graça.</p>
     */
    @Test
    void closeSession_subtractsRefundedCashFromTheExpectedAmount() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        // abertura 10,00 + recebido 100,00 − estornado 30,00 = 80,00
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(new BigDecimal("100.00"));
        when(orderPaymentRepository.sumRefundedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(new BigDecimal("30.00"));
        when(orderRepository.sumChangeAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashRegisterSession closed = pdvService.closeSession(1L, new BigDecimal("80.00"), "gerente");

        assertThat(closed.expectedAmount()).isEqualByComparingTo("80.00");
        assertThat(closed.diverges()).isFalse();
    }

    // ── PDV-C005 — mesa aberta barra o fechamento do caixa ───────────────────────────────────

    /**
     * A única regra do ciclo de caixa que BLOQUEIA em vez de apenas registrar, e a assimetria é
     * deliberada: divergência de contagem é um achado (o dinheiro já é o que é), mas mesa aberta é
     * uma porta que ainda dá para fechar agora e não dará mais depois — {@code addItem} e
     * {@code cancelComanda} exigem a sessão de origem ABERTA, então a comanda que sobreviva ao
     * fechamento congela para sempre, com o estoque já debitado e sem caminho de devolução.
     */
    @Test
    void closeSession_refusesWhenTheSessionStillHasAnOpenComanda() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(comandaRepository.findOpenIdsBySessionId(1L)).thenReturn(List.of(77L));

        assertThatThrownBy(() -> pdvService.closeSession(1L, BigDecimal.TEN, "gerente"))
                .isInstanceOf(CashRegisterSessionHasOpenComandasException.class)
                .hasMessageContaining("77");

        // A sessão não pode ter sido carimbada: fechar pela metade seria o mesmo beco sem saída.
        verify(cashRegisterRepository, never()).save(any());
    }

    /** A barreira é anterior ao cálculo do esperado — não adianta conferir uma gaveta que não fecha. */
    @Test
    void closeSession_doesNotEvenComputeExpectedWhenAMesaIsStillOpen() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(comandaRepository.findOpenIdsBySessionId(1L)).thenReturn(List.of(77L));

        assertThatThrownBy(() -> pdvService.closeSession(1L, BigDecimal.TEN, "gerente"))
                .isInstanceOf(CashRegisterSessionHasOpenComandasException.class);

        verify(orderPaymentRepository, never())
                .sumCapturedAmountBySessionIdAndMethod(any(), any());
    }

    /** Comanda já fechada ou cancelada não segura o caixa — findOpenIdsBySessionId só traz ABERTA. */
    @Test
    void closeSession_proceedsWhenEveryComandaOfTheSessionIsAlreadySettled() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(comandaRepository.findOpenIdsBySessionId(1L)).thenReturn(List.of());
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(BigDecimal.ZERO);
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        givenNoCashOutflows();
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(pdvService.closeSession(1L, BigDecimal.TEN, "gerente").status())
                .isEqualTo(CashRegisterSession.Status.CLOSED);
    }

    @Test
    void getSessionPaymentTotals_returnsAllFourMethodsEvenWhenUnused() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(new BigDecimal("120.00"));
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DEBITO))
                .thenReturn(BigDecimal.ZERO);
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.CREDITO))
                .thenReturn(BigDecimal.ZERO);
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.PIX))
                .thenReturn(new BigDecimal("45.00"));
        when(orderPaymentRepository.sumRefundedAmountBySessionIdAndMethod(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumChangeAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);

        List<PaymentTotal> totals = pdvService.getSessionPaymentTotals(1L);

        assertThat(totals).hasSize(4);
        assertThat(totals).extracting(PaymentTotal::method)
                .containsExactlyInAnyOrder(PaymentMethod.DINHEIRO, PaymentMethod.DEBITO,
                        PaymentMethod.CREDITO, PaymentMethod.PIX);
        assertThat(totals.stream().filter(t -> t.method() == PaymentMethod.DINHEIRO).findFirst().orElseThrow()
                .amount()).isEqualByComparingTo("120.00");
    }

    /**
     * PDV-F026 — o bruto continua em amount; refundedAmount, changeAmount (só DINHEIRO) e netAmount
     * deixam a aba Caixas mostrar o líquido sem abrir recibo.
     */
    @Test
    void getSessionPaymentTotals_netsRefundsAndCashChange() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.DINHEIRO))
                .thenReturn(new BigDecimal("150.00"));
        when(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(1L, PaymentMethod.PIX))
                .thenReturn(new BigDecimal("80.00"));
        when(orderPaymentRepository.sumRefundedAmountBySessionIdAndMethod(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(orderPaymentRepository.sumRefundedAmountBySessionIdAndMethod(1L, PaymentMethod.PIX))
                .thenReturn(new BigDecimal("30.00"));
        when(orderRepository.sumChangeAmountBySessionId(1L)).thenReturn(new BigDecimal("20.00"));

        List<PaymentTotal> totals = pdvService.getSessionPaymentTotals(1L);

        PaymentTotal dinheiro = totals.stream().filter(t -> t.method() == PaymentMethod.DINHEIRO).findFirst().orElseThrow();
        PaymentTotal pix = totals.stream().filter(t -> t.method() == PaymentMethod.PIX).findFirst().orElseThrow();
        assertThat(dinheiro.changeAmount()).isEqualByComparingTo("20.00");
        assertThat(dinheiro.netAmount()).isEqualByComparingTo("130.00");
        assertThat(pix.refundedAmount()).isEqualByComparingTo("30.00");
        assertThat(pix.changeAmount()).isEqualByComparingTo("0");
        assertThat(pix.netAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void closeSession_refusesAnAlreadyClosedSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(closedSession()));

        assertThatThrownBy(() -> pdvService.closeSession(1L, BigDecimal.TEN, "gerente"))
                .isInstanceOf(CashRegisterSessionClosedException.class);

        verify(cashRegisterRepository, never()).save(any());
    }

    // ── PDV-F002: sangria e suprimento ───────────────────────────────────────────────────────

    @Test
    void registerCashMovement_requiresTheSessionToBelongToTheOperator() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.registerCashMovement(1L, CashMovementType.SANGRIA,
                new BigDecimal("50.00"), "cofre", "outro-caixa"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);

        verify(cashMovementRepository, never()).save(any());
    }

    @Test
    void registerCashMovement_persistsWhenTheSessionIsOwnAndOpen() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(cashMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashMovement movement = pdvService.registerCashMovement(1L, CashMovementType.SANGRIA,
                new BigDecimal("50.00"), "depósito no cofre", "caixa1");

        assertThat(movement.type()).isEqualTo(CashMovementType.SANGRIA);
        assertThat(movement.amount()).isEqualByComparingTo("50.00");
        assertThat(movement.signedAmount()).isEqualByComparingTo("-50.00");
    }

    @Test
    void registerCashMovement_refusesOnClosedSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(closedSession()));

        assertThatThrownBy(() -> pdvService.registerCashMovement(1L, CashMovementType.SUPRIMENTO,
                BigDecimal.TEN, "troco", "caixa1"))
                .isInstanceOf(CashRegisterSessionClosedException.class);
    }

    // ── PDV-C004: a venda herda o depósito e exige posse ─────────────────────────────────────

    @Test
    void registerSale_takesTheWarehouseFromTheSessionNotFromTheCaller() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        assertThat(order.warehouseCode()).isEqualTo("LOJA-01");
        verify(estoqueUseCase).adjustStock(any(), eq("LOJA-01"), any(), any(), any(), any());
    }

    @Test
    void registerSale_refusesASessionThatBelongsToAnotherOperator() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "outro-caixa"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Parte D: liquidar no balcão um pedido feito no app ───────────────────────────────────

    /** Pedido montado no aplicativo, com estoque já reservado, esperando pagamento. */
    private static Order pendingOnlineOrder() {
        return Order.of(7L, null, SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO, 42L,
                null, "LOJA-01", List.of(com.cernecommerce.core.domain.model.pedido.OrderItem
                        .fromCatalog("CARV-001", new BigDecimal("2.000"), CARVAO, null)),
                new BigDecimal("44.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("44.00"),
                null, null, Instant.now(), null, null, null, null, 0L);
    }

    @Test
    void settleOnlineOrder_consumesTheReservationInsteadOfDebitingStockAgain() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        pdvService.settleOnlineOrder(1L, 7L, cash("44.00"), "caixa1");

        verify(estoqueUseCase).consumeReservationsByOwner("ORDER:7", "caixa1");
        // Dar baixa aqui debitaria a mercadoria duas vezes: ela já saiu do disponível na reserva.
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void settleOnlineOrder_keepsTheChannelAndAttachesTheCashSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order settled = pdvService.settleOnlineOrder(1L, 7L, cash("44.00"), "caixa1");

        // O canal é a ORIGEM e não muda — senão o relatório de conversão do site mentiria.
        assertThat(settled.channel()).isEqualTo(SalesChannel.MARKETPLACE);
        assertThat(settled.sessionId()).isEqualTo(1L);
        assertThat(settled.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(settled.orderNumber()).isEqualTo("000001001");
        assertThat(settled.paidAt()).isNotNull();
    }

    /**
     * PDV-C015 — era o único caminho de recebimento do projeto que não gravava linha de pagamento.
     * Sem ela, {@code closeSession} (que soma {@code order_payment}, não pedido) não esperava a
     * cédula, e o turno fechava acusando sobra sem dono.
     */
    @Test
    void settleOnlineOrder_recordsTheCapturedPaymentInTheReceivingSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        pdvService.settleOnlineOrder(1L, 7L, cash("44.00"), "caixa1");

        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository).save(captor.capture());
        OrderPayment gravado = captor.getValue();
        assertThat(gravado.method()).isEqualTo(PaymentMethod.DINHEIRO);
        assertThat(gravado.amount()).isEqualByComparingTo("44.00");
        assertThat(gravado.status()).isEqualTo(PaymentStatus.CAPTURED);
    }

    /** PDV-F025 — canal e operadora chegam até a linha de pagamento gravada. */
    @Test
    void settleOnlineOrder_recordsTheChannelAndProviderOfTheCharge() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        pdvService.settleOnlineOrder(1L, 7L, List.of(new PaymentCommand(PaymentMethod.DEBITO,
                new BigDecimal("44.00"), null, PaymentChannel.MAQUININHA, PaymentProvider.INFINITYPAY)), "caixa1");

        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository).save(captor.capture());
        assertThat(captor.getValue().channel()).isEqualTo(PaymentChannel.MAQUININHA);
        assertThat(captor.getValue().provider()).isEqualTo(PaymentProvider.INFINITYPAY);
    }

    @Test
    void paymentCommand_cashWithChannelOrProviderWithoutChannel_isRefused() {
        assertThatThrownBy(() -> new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null,
                PaymentChannel.LINK, null)).isInstanceOf(InvalidPaymentChannelException.class);
        assertThatThrownBy(() -> new PaymentCommand(PaymentMethod.PIX, BigDecimal.TEN, null,
                null, PaymentProvider.CIELO)).isInstanceOf(InvalidPaymentChannelException.class);
    }

    /**
     * PDV-C015 — a cobrança de gateway aberta no checkout é encerrada, não deixada pendurada.
     *
     * <p>{@code ShopService.checkout} grava uma {@code PENDING}/{@code GATEWAY_PIX} em todo pedido
     * de marketplace. Pago no balcão, nenhum webhook vai confirmá-la: mantida {@code PENDING} ela
     * descreveria para sempre uma cobrança em aberto que não existe.</p>
     */
    @Test
    void settleOnlineOrder_cancelsThePendingGatewayChargeFromCheckout() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        OrderPayment doCheckout = OrderPayment.of(99L, 7L, PaymentMethod.GATEWAY_PIX,
                new BigDecimal("44.00"), PaymentStatus.PENDING, null, null, null, null, Instant.now());
        when(orderPaymentRepository.findByOrderId(7L)).thenReturn(List.of(doCheckout));

        pdvService.settleOnlineOrder(1L, 7L, cash("44.00"), "caixa1");

        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository, times(2)).save(captor.capture());
        OrderPayment encerrada = captor.getAllValues().stream()
                .filter(p -> p.method() == PaymentMethod.GATEWAY_PIX)
                .findFirst().orElseThrow();
        assertThat(encerrada.status()).isEqualTo(PaymentStatus.CANCELLED);
        // A MESMA linha, não uma nova ao lado — senão a PENDING continuaria de pé.
        assertThat(encerrada.id()).isEqualTo(99L);
        assertThat(encerrada.capturedAt()).isNull();
    }

    /** Mesma ordem de registerSale: pagamento recusado não custa uma reserva consumida. */
    @Test
    void settleOnlineOrder_refusesInsufficientPaymentBeforeConsumingTheReservation() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(1L, 7L, cash("40.00"), "caixa1"))
                .isInstanceOf(InsufficientPaymentException.class);

        verify(estoqueUseCase, never()).consumeReservationsByOwner(any(), any());
        verify(orderRepository, never()).save(any());
        verify(orderPaymentRepository, never()).save(any());
    }

    /**
     * PDV-C015 — aqui não há troco: o canal continua {@code MARKETPLACE} e {@code Order} recusa
     * {@code changeAmount} ali. Aceitar o excedente sem ter onde gravá-lo faria a linha de
     * pagamento afirmar que entrou na gaveta mais do que ficou — o defeito que PDV-C017 acabou de
     * tirar do fechamento, voltando por outra porta.
     */
    @Test
    void settleOnlineOrder_refusesPaymentAboveTheNetAmountBecauseThereIsNowhereToPutChange() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(1L, 7L, cash("50.00"), "caixa1"))
                .isInstanceOf(ChangeNotSupportedException.class);

        verify(estoqueUseCase, never()).consumeReservationsByOwner(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void settleOnlineOrder_refusesAnOrderThatIsNotAwaitingPayment() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L))
                .thenReturn(Optional.of(pendingOnlineOrder().cancelled("desistiu", Instant.now())));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(1L, 7L, cash("44.00"), "caixa1"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void settleOnlineOrder_refusesASessionThatBelongsToAnotherOperator() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(1L, 7L, cash("44.00"), "outro-caixa"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);

        verify(estoqueUseCase, never()).consumeReservationsByOwner(any(), any());
    }

    @Test
    void listPendingOnlineOrders_filtersByChannelAndStatus() {
        when(orderRepository.findAll(SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO,
                null, null, null, 0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        pdvService.listPendingOnlineOrders(0, 20);

        verify(orderRepository).findAll(SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO,
                null, null, null, 0, 20);
    }

    @Test
    void listSessions_delegatesToRepository() {
        PageResult<CashRegisterSession> page = new PageResult<>(List.of(openSession()), 0, 20, 1L, 1);
        when(cashRegisterRepository.findAll(0, 20)).thenReturn(page);

        assertThat(pdvService.listSessions(0, 20).content()).hasSize(1);
    }

    // ── PDV-F004: preço e custo vêm do catálogo ──────────────────────────────────────────────

    @Test
    void registerSale_resolvesPriceAndCostFromTheCatalog() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        StockBalance balance = StockBalance.of(1L, "CARV-001", 2L, new BigDecimal("18.000"), 1L);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(balance);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        // 2 x 22,00 — o preço não veio do chamador em lugar nenhum.
        assertThat(order.grossAmount()).isEqualByComparingTo("44.00");
        assertThat(order.items()).singleElement().satisfies(item -> {
            assertThat(item.unitPrice()).isEqualByComparingTo("22.00");
            assertThat(item.costPrice()).isEqualByComparingTo("18.00");
            assertThat(item.productName()).isEqualTo("Carvao Coco");
        });
    }

    // ── CRM-F003: cashback ───────────────────────────────────────────────────────────────────

    @Test
    void registerSale_stampsCashbackPercentWhenARateApplies() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(cashbackUseCase.resolveApplicableRate("CARV-001"))
                .thenReturn(CashbackRate.global(new BigDecimal("3.0")));

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        assertThat(order.items()).singleElement()
                .satisfies(item -> assertThat(item.cashbackPercent()).isEqualByComparingTo("3.0"));
    }

    @Test
    void registerSale_leavesCashbackPercentNullWhenNoRateApplies() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(cashbackUseCase.resolveApplicableRate("CARV-001")).thenReturn(null);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        assertThat(order.items()).singleElement().satisfies(item -> assertThat(item.cashbackPercent()).isNull());
    }

    @Test
    void registerSale_recordsEarnedCashbackAfterSavingTheConcludedOrder() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, 42L, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        verify(cashbackUseCase).recordEarnedForOrder(order);
    }

    @Test
    void registerSale_refusesProductWithoutPriceBeforeTouchingStock() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.resolveSaleInfo("SEM-PRECO")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", Pricing.empty()));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(new SaleItemCommand("SEM-PRECO", BigDecimal.ONE, null)), cash("1.00"), "caixa1"))
                .isInstanceOf(ProductNotPricedException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Desconto ─────────────────────────────────────────────────────────────────────────────

    @Test
    void registerSale_appliesDiscountWithinTheLimit() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        // 4,00 sobre 44,00 = 9,09% — abaixo do teto de 10%.
        Order order = pdvService.registerSale(1L, null,
                List.of(twoCharcoals(new BigDecimal("4.00"))), cash("40.00"), "caixa1");

        assertThat(order.discountAmount()).isEqualByComparingTo("4.00");
        assertThat(order.netAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void registerSale_refusesDiscountAboveTheLimitBeforeTouchingStock() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));

        // 5,00 sobre 44,00 = 11,36% — acima do teto de 10%.
        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(twoCharcoals(new BigDecimal("5.00"))), cash("39.00"), "caixa1"))
                .isInstanceOf(DiscountLimitExceededException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    /**
     * PDV-C016 — desconto de linha acima do bruto dela responde 409 com código próprio.
     *
     * <p>A regra existe desde sempre no compact constructor de {@code OrderItem}, mas subia como
     * {@code IllegalArgumentException}, que o handler global achata num 400 genérico
     * ({@code BAD_REQUEST}, "Requisição inválida") descartando a mensagem do domínio — a mesma
     * resposta de qualquer corpo malformado. Não confundir com {@code DISCOUNT_LIMIT_EXCEEDED},
     * que é o teto percentual da casa: aqui o desconto é aritmeticamente impossível, independente
     * de teto.</p>
     */
    @Test
    void registerSale_refusesItemDiscountAboveTheLineGrossBeforeTouchingStock() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));

        // 44,01 de desconto sobre uma linha de 44,00: não existe rateio possível.
        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(twoCharcoals(new BigDecimal("44.01"))), cash("0.01"), "caixa1"))
                .isInstanceOf(ItemDiscountExceedsGrossException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Conclusão e numeração ────────────────────────────────────────────────────────────────

    @Test
    void registerSale_concludesInTheSameTransactionAndStampsTheOrderNumber() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        assertThat(order.channel()).isEqualTo(SalesChannel.BALCAO);
        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(order.orderNumber()).isEqualTo("000001000");
        assertThat(order.concludedAt()).isNotNull();
        assertThat(order.paidAt()).isNotNull();
    }

    // PDV-F008 — reserva para retirada

    @Test
    void registerSale_reserveForPickupTrue_savesReservadoInsteadOfConcluido() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1", true);

        assertThat(order.status()).isEqualTo(OrderStatus.RESERVADO);
        assertThat(order.orderNumber()).as("numeração é consumida na reserva, igual na conclusão").isNotNull();
        assertThat(order.reservedAt()).isNotNull();
        assertThat(order.concludedAt()).isNull();
    }

    @Test
    void registerSale_reserveForPickupTrue_aindaBaixaEstoqueECapturaPagamento() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1", true);

        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(new BigDecimal("2.000")), eq("Venda balcão sessão #1"), eq("caixa1"));
        verify(orderPaymentRepository).save(any());
    }

    @Test
    void registerSale_reserveForPickupFalse_comportamentoInalterado() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1", false);

        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
    }

    @Test
    void registerSale_keepsTheCustomerWhenIdentified() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, 42L, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        assertThat(order.customerId()).isEqualTo(42L);
    }

    @Test
    void registerSale_allowsAnonymousSale() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        assertThat(pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1")
                .customerId()).isNull();
    }

    // ── Baixa de estoque ─────────────────────────────────────────────────────────────────────

    @Test
    void registerSale_adjustsStockPerItemWithTheSessionInTheReason() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(new BigDecimal("2.000")), eq("Venda balcão sessão #1"), eq("caixa1"));
    }

    @Test
    void registerSale_throwsWhenSessionNotFound() {
        when(cashRegisterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.registerSale(99L, null,
                List.of(twoCharcoals(null)), cash("44.00"), "caixa1"))
                .isInstanceOf(CashRegisterSessionNotFoundException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void registerSale_throwsWhenSessionIsClosed() {
        CashRegisterSession closed = CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN,
                "LOJA-01", Instant.now(), "gerente", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                CashRegisterSession.Status.CLOSED);
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(twoCharcoals(null)), cash("44.00"), "caixa1"))
                .isInstanceOf(CashRegisterSessionClosedException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void registerSale_propagatesUnknownSkuAndDoesNotSaveOrder() {
        // EST-C002: antes, um SKU digitado errado criava saldo e ledger órfãos e a venda era
        // gravada normalmente. Agora a resolução de preço já barra, antes de tocar o estoque.
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.resolveSaleInfo("SKU-FANTASMA"))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(new SaleItemCommand("SKU-FANTASMA", BigDecimal.ONE, null)), cash("1.00"), "caixa1"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void registerSale_propagatesInsufficientStockAndDoesNotSaveOrder() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientStockException("CARV-001", 2L, new BigDecimal("1.000"),
                        new BigDecimal("2.000")));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(twoCharcoals(null)), cash("44.00"), "caixa1"))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderRepository, never()).save(any());
    }

    // ── PDV-F006: pagamento ──────────────────────────────────────────────────────────────────

    @Test
    void registerSale_capturesPaymentAndPersistsIt() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        var captor = org.mockito.ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository).save(captor.capture());
        OrderPayment saved = captor.getValue();
        assertThat(saved.orderId()).isEqualTo(order.id());
        assertThat(saved.method()).isEqualTo(PaymentMethod.DINHEIRO);
        assertThat(saved.amount()).isEqualByComparingTo("44.00");
        assertThat(saved.status()).isEqualTo(com.cernecommerce.core.domain.model.pagamento.PaymentStatus.CAPTURED);
    }

    @Test
    void registerSale_computesChangeFromCashOverpayment() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        // Pedido de 44,00, cliente entrega 50,00 em dinheiro.
        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("50.00"), "caixa1");

        assertThat(order.changeAmount()).isEqualByComparingTo("6.00");
    }

    @Test
    void registerSale_noChangeWhenPaymentMatchesExactly() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1");

        assertThat(order.changeAmount()).isNull();
    }

    /**
     * Pagamento dividido: R$30 no débito (exato, não pode gerar troco) + R$20 em dinheiro para
     * cobrir o restante (R$14) e sobrar R$6 — o troco só pode vir do dinheiro.
     */
    @Test
    void registerSale_computesChangeFromCashPortionOnlyInASplitPayment() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        List<PaymentCommand> split = List.of(
                new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal("30.00"), null),
                new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("20.00"), null));

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), split, "caixa1");

        assertThat(order.changeAmount()).isEqualByComparingTo("6.00");
    }

    @Test
    void registerSale_throwsInsufficientPaymentBeforeTouchingStock() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null, List.of(twoCharcoals(null)),
                cash("40.00"), "caixa1"))
                .isInstanceOf(InsufficientPaymentException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    /** Só dinheiro pode ser tendido a mais. Débito sozinho passando do total é erro, não troco. */
    @Test
    void registerSale_throwsWhenNonCashPaymentAloneExceedsTheOrderTotal() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));

        List<PaymentCommand> debitoAcimaDoTotal = List.of(
                new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal("50.00"), null));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null, List.of(twoCharcoals(null)),
                debitoAcimaDoTotal, "caixa1"))
                .isInstanceOf(PaymentExceedsOrderTotalException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrderPayments_delegatesToRepositoryAfterConfirmingTheOrderExists() {
        Order stored = Order.of(7L, "000001000", SalesChannel.BALCAO, OrderStatus.CONCLUIDO, null, 1L,
                "LOJA-01", List.of(com.cernecommerce.core.domain.model.pedido.OrderItem.of(1L, "CARV-001",
                        new BigDecimal("2.000"), new BigDecimal("22.00"), new BigDecimal("18.00"),
                        BigDecimal.ZERO, null)),
                new BigDecimal("44.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("44.00"),
                null, null, Instant.now(), Instant.now(), Instant.now(), null, null, 0L);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(stored));
        OrderPayment payment = OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("44.00"), null);
        when(orderPaymentRepository.findByOrderId(7L)).thenReturn(List.of(payment));

        assertThat(pdvService.getOrderPayments(7L)).containsExactly(payment);
    }

    @Test
    void getOrderPayments_throwsWhenOrderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.getOrderPayments(99L)).isInstanceOf(OrderNotFoundException.class);
        verify(orderPaymentRepository, never()).findByOrderId(any());
    }

    // ── PDV-F005: leitura ────────────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsThePersistedOrder() {
        Order stored = Order.of(7L, "000001000", SalesChannel.BALCAO, OrderStatus.CONCLUIDO, null, 1L,
                "LOJA-01", List.of(com.cernecommerce.core.domain.model.pedido.OrderItem.of(1L, "CARV-001",
                        new BigDecimal("2.000"), new BigDecimal("22.00"), new BigDecimal("18.00"),
                        BigDecimal.ZERO, null)),
                new BigDecimal("44.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("44.00"),
                null, null, Instant.now(), Instant.now(), Instant.now(), null, null, 0L);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(stored));

        assertThat(pdvService.getOrder(7L).orderNumber()).isEqualTo("000001000");
    }

    @Test
    void getOrder_throwsWhenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.getOrder(99L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listSessionOrders_requiresAnExistingSession() {
        when(cashRegisterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.listSessionOrders(99L, 0, 20))
                .isInstanceOf(CashRegisterSessionNotFoundException.class);

        verify(orderRepository, never()).findBySessionId(any(), anyInt(), anyInt());
    }

    @Test
    void listSessionOrders_delegatesToRepository() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findBySessionId(1L, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        assertThat(pdvService.listSessionOrders(1L, 0, 20).content()).isEmpty();
    }

    // ── PDV-F022: entrega, observação por item e caixa por dia ──────────────────────────────

    private static OrderDelivery entregaMotoboy(String fee) {
        return new OrderDelivery(DeliveryType.ENTREGA,
                new DeliveryAddress("Rua A", "10", null, "58000-000", "Centro", "João Pessoa", "PB", "Brasil", null),
                DeliveryMethod.MOTOBOY_LOJA, "Zé", "83999990000", null, null, null, new BigDecimal(fee));
    }

    private void givenCharcoalOnCatalog() {
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvao Coco", CARVAO));
    }

    @Test
    void registerSale_withEntrega_reservesAndChargesTheFeeOnTopOfNet() {
        givenOpenSessionAndPersistence();
        givenCharcoalOnCatalog();

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("52.00"), "caixa1",
                false, entregaMotoboy("8.00"));

        assertThat(order.status()).isEqualTo(OrderStatus.RESERVADO);
        assertThat(order.netAmount()).isEqualByComparingTo("44.00");
        assertThat(order.totalPayable()).isEqualByComparingTo("52.00");
        assertThat(order.delivery().courierName()).isEqualTo("Zé");
        assertThat(order.allowedTransitions()).contains(OrderStatus.SEPARADO);
    }

    @Test
    void registerSale_withEntrega_refusesPaymentThatCoversOnlyTheNet() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        givenCharcoalOnCatalog();

        assertThatThrownBy(() -> pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"),
                "caixa1", false, entregaMotoboy("8.00")))
                .isInstanceOf(InsufficientPaymentException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void registerSale_withRetiradaImediata_concludes() {
        givenOpenSessionAndPersistence();
        givenCharcoalOnCatalog();

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1",
                false, retirada());

        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(order.totalPayable()).isEqualByComparingTo("44.00");
    }

    @Test
    void registerSale_withRetiradaAndReserveForPickup_reservesButCannotEnterTheShippingPipeline() {
        givenOpenSessionAndPersistence();
        givenCharcoalOnCatalog();

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1",
                true, retirada());

        assertThat(order.status()).isEqualTo(OrderStatus.RESERVADO);
        assertThat(order.allowedTransitions()).doesNotContain(OrderStatus.SEPARADO);
    }

    private static OrderDelivery retirada() {
        return new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null, null);
    }

    @Test
    void registerSale_persistsTheItemNote() {
        givenOpenSessionAndPersistence();
        givenCharcoalOnCatalog();

        Order order = pdvService.registerSale(1L, null,
                List.of(new SaleItemCommand("CARV-001", new BigDecimal("2.000"), null, "  Sem gelo ")),
                cash("44.00"), "caixa1");

        assertThat(order.items().get(0).notes()).isEqualTo("Sem gelo");
    }

    @Test
    void registerSale_refusesASessionOpenedOnAPreviousDayInStoreTime() {
        // 26/09 às 00:30 em São Paulo (03:30 UTC); o caixa abriu 25/09 às 22:00 em São Paulo
        // (01:00 UTC de 26/09 — mesmo dia em UTC, dia anterior na loja).
        Clock clock = Clock.fixed(Instant.parse("2026-09-26T03:30:00Z"), ZoneOffset.UTC);
        PdvService service = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository,
                MAX_DISCOUNT_PERCENT, clock);
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(CashRegisterSession.of(1L, "caixa1",
                Instant.parse("2026-09-26T01:00:00Z"), BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN)));

        assertThatThrownBy(() -> service.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1"))
                .isInstanceOf(CashRegisterSessionStaleException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerSale_acceptsASessionOpenedEarlierTheSameStoreDay() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-27T02:00:00Z"), ZoneOffset.UTC); // 26/09 23:00 SP
        PdvService service = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository,
                MAX_DISCOUNT_PERCENT, clock);
        givenOpenSessionAndPersistence();
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(CashRegisterSession.of(1L, "caixa1",
                Instant.parse("2026-09-26T11:00:00Z"), BigDecimal.TEN, "LOJA-01", // 26/09 08:00 SP
                null, null, null, null, null, CashRegisterSession.Status.OPEN)));
        givenCharcoalOnCatalog();

        assertThat(service.registerSale(1L, null, List.of(twoCharcoals(null)), cash("44.00"), "caixa1").status())
                .isEqualTo(OrderStatus.CONCLUIDO);
    }
}
