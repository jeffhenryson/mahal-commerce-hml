package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.exception.pedido.OrderHasNoDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.OrderDeliveryNotEditableException;
import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentCorrectionRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.domain.exception.pagamento.CashSessionClosedForCorrectionException;
import com.cernecommerce.core.domain.exception.pagamento.CorrectionReasonRequiredException;
import com.cernecommerce.core.domain.exception.pagamento.GatewayPaymentNotCorrectableException;
import com.cernecommerce.core.domain.exception.pagamento.OrderNotCorrectableException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentTotalMismatchException;
import com.cernecommerce.core.domain.model.pagamento.CashSessionAdjustment;
import com.cernecommerce.core.domain.model.pagamento.OrderPaymentCorrection;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Mock OrderRepository orderRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock OrderPaymentCorrectionRepository correctionRepository;
    @Mock com.cernecommerce.core.ports.in.ReceivableUseCase receivableUseCase;

    OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, estoqueUseCase, orderPaymentRepository, cashbackUseCase,
                cashRegisterRepository, correctionRepository, receivableUseCase);
    }

    private static List<OrderItem> twoCharcoals() {
        return List.of(OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")), null));
    }

    /** Venda de balcão já concluída — o caso mais comum de reembolso. */
    private static Order concludedBalcao() {
        return Order.openBalcao(1L, "LOJA-01", null, twoCharcoals())
                .concluded("000001000", null, NOW);
    }

    private static Order paidMarketplace() {
        return Order.openMarketplace(42L, "LOJA-01", twoCharcoals()).paid(NOW);
    }

    /** Pedido de marketplace ainda aguardando pagamento — o caso normal de cancelamento. */
    private static Order pendingMarketplace() {
        return Order.openMarketplace(42L, "LOJA-01", twoCharcoals());
    }

    /** Venda de balcão reservada para retirada depois (PDV-F008). */
    private static Order reservedBalcao() {
        return Order.openBalcao(1L, "LOJA-01", null, twoCharcoals())
                .reserved("000001000", null, NOW);
    }

    // ── Leitura ──────────────────────────────────────────────────────────────────────────────

    @Test
    void getOrder_throwsWhenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listOrders_passesEveryFilterThrough() {
        Instant from = NOW.minusSeconds(3600);
        when(orderRepository.findAll(SalesChannel.MARKETPLACE, OrderStatus.PAGO, 42L, from, NOW, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        orderService.listOrders(SalesChannel.MARKETPLACE, OrderStatus.PAGO, 42L, from, NOW, 0, 20);

        verify(orderRepository).findAll(SalesChannel.MARKETPLACE, OrderStatus.PAGO, 42L, from, NOW, 0, 20);
    }

    @Test
    void listOrders_acceptsAllFiltersNull() {
        when(orderRepository.findAll(null, null, null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        assertThat(orderService.listOrders(null, null, null, null, null, 0, 20).content()).isEmpty();
    }

    // ── Transição de estágio ─────────────────────────────────────────────────────────────────

    @Test
    void changeStatus_advancesThroughTheFulfillmentChain() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidMarketplace()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.changeStatus(1L, OrderStatus.SEPARADO, "operador");

        assertThat(order.status()).isEqualTo(OrderStatus.SEPARADO);
    }

    @Test
    void changeStatus_refusesSkippingSteps() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidMarketplace()));

        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.ENTREGUE, "operador"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    // ── PED-C006: o caminho genérico é só a esteira ─────────────────────────────────────────

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = OrderStatus.class, names = {"PAGO", "CONCLUIDO"})
    void changeStatus_pedidoAguardandoPagamento_naoViraPagoNemConcluidoSemPagamento(OrderStatus alvo) {
        // A tabela de OrderStatus permite as duas — mas só para o webhook e o settleOnlineOrder,
        // que gravam o pagamento, numeram o pedido e consomem a reserva. Aqui nada disso aconteceria.
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingMarketplace()));

        assertThatThrownBy(() -> orderService.changeStatus(1L, alvo, "operador"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = OrderStatus.class, names = {"REEMBOLSADO", "CANCELADO"})
    void changeStatus_reembolsoECancelamentoTemRotaPropria(OrderStatus alvo) {
        // Antes estouravam IllegalArgumentException no construtor de Order (400 genérico) — e no
        // bulk-status, no meio do laço (PED-C008).
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidMarketplace()));

        assertThatThrownBy(() -> orderService.changeStatus(1L, alvo, "operador"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    // ── Retirada de venda reservada (PDV-F008) ──────────────────────────────────────────────

    @Test
    void changeStatus_reservadoParaConcluido_usaPickedUpEStampaConcludedAt() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(reservedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.changeStatus(1L, OrderStatus.CONCLUIDO, "operador");

        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(order.concludedAt()).as("pickedUp() carimba concludedAt, diferente do withStatus genérico")
                .isNotNull();
        assertThat(order.reservedAt()).as("histórico preservado").isEqualTo(NOW);
    }

    @Test
    void changeStatus_advancesGenericStatus_doesNotUsePickedUpWhenNotComingFromReservado() {
        // Confirma que o caminho pickedUp() só é usado a partir de RESERVADO — a esteira normal
        // (PAGO -> SEPARADO, já coberta acima) continua passando por withStatus.
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidMarketplace()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.changeStatus(1L, OrderStatus.SEPARADO, "operador");

        assertThat(order.concludedAt()).isNull();
    }

    // ── Cancelamento pré-pagamento (libera reserva, nunca toca estoque real) ─────────────────

    @Test
    void cancelOrder_releasesTheReservationInsteadOfTouchingRealStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingMarketplace()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.releaseReservationsByOwner(any(), any())).thenReturn(1);

        Order cancelled = orderService.cancelOrder(1L, "cliente desistiu", "gerente");

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELADO);
        assertThat(cancelled.cancelReason()).isEqualTo("cliente desistiu");
        assertThat(cancelled.cancelledAt()).isNotNull();
        verify(estoqueUseCase).releaseReservationsByOwner(eq(Order.reservationOwnerReference(1L)), eq("gerente"));
        // Nunca houve baixa real para devolver: só reserva.
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelOrder_refusesToCancelTwiceAndDoesNotReleaseAgain() {
        Order alreadyCancelled = pendingMarketplace().cancelled("primeiro", NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(alreadyCancelled));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "segundo", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        // O ponto do teste: um segundo cancelamento liberaria a reserva de novo.
        verify(estoqueUseCase, never()).releaseReservationsByOwner(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_propagatesReservationReleaseFailureAndDoesNotPersist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingMarketplace()));
        when(estoqueUseCase.releaseReservationsByOwner(any(), any()))
                .thenThrow(new IllegalStateException("reserva não encontrada"));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "engano", "gerente"))
                .isInstanceOf(IllegalStateException.class);

        verify(orderRepository, never()).save(any());
    }

    /**
     * Cancelar e reembolsar são eventos diferentes (PDV-F007): pedido com pagamento confirmado
     * usa {@code refundOrder}, nunca {@code cancelOrder}.
     */
    @Test
    void cancelOrder_refusesOrdersThatAlreadyHavePaymentConfirmed() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidMarketplace()));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "engano", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(estoqueUseCase, never()).releaseReservationsByOwner(any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Reembolso pós-pagamento: estoque (EST-F014), pagamento e cashback (PDV-F007) ─────────

    @Test
    void refundOrder_returnsTheGoodsToStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        Order refunded = orderService.refundOrder(1L, "cliente desistiu", "gerente");

        assertThat(refunded.status()).isEqualTo(OrderStatus.REEMBOLSADO);
        assertThat(refunded.cancelReason()).isEqualTo("cliente desistiu");
        assertThat(refunded.refundedAt()).isNotNull();
        // ENTRADA, não SAIDA: devolução devolve mercadoria à prateleira.
        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("2.000")), any(), eq("gerente"), isNull(), isNull());
    }

    /** PDV-F021 — a sessão do cardápio nunca saiu do estoque (SKU sintético): o estorno só devolve o resto. */
    @Test
    void refundOrder_skipsMenuSessionLines() {
        List<OrderItem> items = List.of(
                OrderItem.of(null, "SESS-2", BigDecimal.ONE, new BigDecimal("30.00"), null, BigDecimal.ZERO, null,
                        "Sessão Premium", ConsumptionMode.SESSAO, false, "Zomo Blueberry", null),
                twoCharcoals().get(0));
        Order order = Order.openBalcao(1L, "LOJA-01", null, items).concluded("000001000", null, NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        orderService.refundOrder(1L, "erro de lançamento", "gerente");

        verify(estoqueUseCase, never()).adjustStock(eq("SESS-2"), any(), any(), any(), any(), any(), any(), any());
        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("2.000")), any(), eq("gerente"), isNull(), isNull());
    }

    @Test
    void refundOrder_stampsTheOrderNumberInTheMovementReason() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        orderService.refundOrder(1L, "engano", "gerente");

        // Sem o número no motivo, a trilha do movimento não é reconstruível.
        verify(estoqueUseCase).adjustStock(any(), any(), any(), any(),
                eq("Reembolso do pedido 000001000"), any(), any(), any());
    }

    @Test
    void refundOrder_worksOnADeliveredOrderBecauseThatIsAReturn() {
        Order delivered = paidMarketplace()
                .withStatus(OrderStatus.SEPARADO)
                .withStatus(OrderStatus.ENVIADO)
                .withStatus(OrderStatus.ENTREGUE);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(delivered));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        Order refunded = orderService.refundOrder(1L, "devolução no prazo", "gerente");

        assertThat(refunded.status()).isEqualTo(OrderStatus.REEMBOLSADO);
        verify(estoqueUseCase).adjustStock(any(), any(), eq(MovementType.ENTRADA), any(), any(), any(), any(), any());
    }

    @Test
    void refundOrder_comLoteInformado_propagaParaAdjustStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        orderService.refundOrder(1L, "devolução no prazo", "gerente",
                List.of(new OrderUseCase.RefundItemLot("CARV-001", "L1", LocalDate.parse("2027-01-01"))));

        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("2.000")), any(), eq("gerente"), eq("L1"), eq(LocalDate.parse("2027-01-01")));
    }

    @Test
    void refundOrder_comLoteDeOutroSku_naoAfetaItemSemCorrespondencia() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        // Casamento é por sku: lote de um SKU que não está no pedido não deve vazar para CARV-001.
        orderService.refundOrder(1L, "devolução no prazo", "gerente",
                List.of(new OrderUseCase.RefundItemLot("ESSE-001", "L1", LocalDate.parse("2027-01-01"))));

        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("2.000")), any(), eq("gerente"), isNull(), isNull());
    }

    @Test
    void refundOrder_refusesToRefundTwiceAndDoesNotTouchStock() {
        Order alreadyRefunded = concludedBalcao().refunded("primeiro", NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(alreadyRefunded));

        assertThatThrownBy(() -> orderService.refundOrder(1L, "segundo", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        // O ponto do teste: um segundo reembolso devolveria a mercadoria de novo, inflando o saldo.
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void refundOrder_propagatesStockFailureAndDoesNotPersist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("depósito inativo"));

        assertThatThrownBy(() -> orderService.refundOrder(1L, "engano", "gerente"))
                .isInstanceOf(IllegalStateException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void refundOrder_reversesEachCapturedPaymentWithMatchingMethodAndAmount() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        OrderPayment cash = OrderPayment.captured(1L, PaymentMethod.DINHEIRO, new BigDecimal("50.00"), null);
        OrderPayment debit = OrderPayment.captured(1L, PaymentMethod.DEBITO, new BigDecimal("30.00"), null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of(cash, debit));
        when(orderPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.refundOrder(1L, "devolução no prazo", "gerente");

        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(OrderPayment::method, OrderPayment::amount, OrderPayment::status)
                .containsExactlyInAnyOrder(
                        tuple(PaymentMethod.DINHEIRO, new BigDecimal("50.00"), PaymentStatus.REFUNDED),
                        tuple(PaymentMethod.DEBITO, new BigDecimal("30.00"), PaymentStatus.REFUNDED));
    }

    @Test
    void refundOrder_skipsPaymentsThatAreNotCaptured() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        OrderPayment failed = OrderPayment.of(2L, 1L, PaymentMethod.DINHEIRO, new BigDecimal("10.00"),
                PaymentStatus.FAILED, null, null, Instant.now(), null, Instant.now());
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of(failed));

        orderService.refundOrder(1L, "engano", "gerente");

        verify(orderPaymentRepository, never()).save(any());
    }

    @Test
    void refundOrder_invokesCashbackReversalForTheOrder() {
        Order order = concludedBalcao();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        orderService.refundOrder(1L, "devolução no prazo", "gerente");

        verify(cashbackUseCase).reverseEarningsForOrder(order);
    }

    /** Pré-pagamento nunca tem o que reembolsar — só {@code cancelOrder} faz sentido. */
    @Test
    void refundOrder_refusesOrdersThatHaveNotBeenPaidYet() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingMarketplace()));

        assertThatThrownBy(() -> orderService.refundOrder(1L, "engano", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── PDV-F022: edição da entrega depois da venda ──────────────────────────────────────────

    private static OrderDelivery correios() {
        return new OrderDelivery(DeliveryType.ENTREGA,
                new DeliveryAddress("Rua A", "10", null, null, null, "João Pessoa", "PB", null, null),
                DeliveryMethod.CORREIOS, null, null, null, null, null, new BigDecimal("15.00"));
    }

    private static OrderDelivery.Patch tracking(String code) {
        return new OrderDelivery.Patch(null, null, null, null, null, null, null, code, null);
    }

    @Test
    void updateDelivery_savesTheMergedDelivery() {
        Order order = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals(), correios())
                .reserved("000000001", null, NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order updated = orderService.updateDelivery(1L, tracking("BR1BR"), "gerente");

        assertThat(updated.delivery().trackingCode()).isEqualTo("BR1BR");
        assertThat(updated.delivery().fee()).isEqualByComparingTo("15.00");
    }

    @Test
    void updateDelivery_refusesOrderWithoutDelivery() {
        Order order = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals()).concluded("000000001", null, NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDelivery(1L, tracking("X"), "gerente"))
                .isInstanceOf(OrderHasNoDeliveryException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateDelivery_refusesRefundedOrder() {
        Order order = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals(), correios())
                .reserved("000000001", null, NOW).refunded("desistiu", NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDelivery(1L, tracking("X"), "gerente"))
                .isInstanceOf(OrderDeliveryNotEditableException.class);
    }

    // ── PDV-F030: correção da forma de pagamento ────────────────────────────────────────────

    private static OrderPayment capturedLine(long id, PaymentMethod method, String amount) {
        return OrderPayment.of(id, 7L, method, new BigDecimal(amount), PaymentStatus.CAPTURED, null, null,
                NOW, NOW, NOW);
    }

    private static CashRegisterSession session(CashRegisterSession.Status status) {
        if (status == CashRegisterSession.Status.CLOSED) {
            return CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.ZERO, "LOJA-01", NOW, "caixa1",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, status);
        }
        return CashRegisterSession.of(1L, "caixa1", NOW, BigDecimal.ZERO, "LOJA-01", null, null,
                null, null, null, status);
    }

    private static List<PaymentCommand> debit(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal(amount), null, null, null));
    }

    private void givenCorrectableOrder(Order order, CashRegisterSession.Status sessionStatus,
            OrderPayment... lines) {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderPaymentRepository.findByOrderId(7L)).thenReturn(List.of(lines));
        lenient().when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(sessionStatus)));
        lenient().when(correctionRepository.save(any())).thenAnswer(inv -> {
            OrderPaymentCorrection c = inv.getArgument(0);
            return new OrderPaymentCorrection(30L, c.orderId(), c.reason(), c.correctedBy(), c.correctedAt(),
                    c.cashSessionId(), c.sessionWasClosed());
        });
        lenient().when(orderPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void correctPayments_retiresCapturedLinesAndCapturesTheNewOnes() {
        givenCorrectableOrder(concludedBalcao(), CashRegisterSession.Status.OPEN,
                capturedLine(1L, PaymentMethod.PIX, "44.00"));

        OrderUseCase.PaymentCorrectionResult result = orderService.correctPayments(7L, debit("44.00"),
                "  marcou PIX, foi débito ", "ana", false);

        assertThat(result.before()).singleElement().satisfies(p -> {
            assertThat(p.status()).isEqualTo(PaymentStatus.CORRECTED);
            assertThat(p.correctionId()).isEqualTo(30L);
            assertThat(p.correctedBy()).isEqualTo("ana");
            assertThat(p.capturedAt()).isEqualTo(NOW);
        });
        assertThat(result.after()).singleElement().satisfies(p -> {
            assertThat(p.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(p.method()).isEqualTo(PaymentMethod.DEBITO);
            assertThat(p.originCorrectionId()).isEqualTo(30L);
        });
        assertThat(result.correction().reason()).isEqualTo("marcou PIX, foi débito");
        assertThat(result.correction().sessionWasClosed()).isFalse();
        verify(correctionRepository, never()).saveAdjustment(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void correctPayments_cashWithChangeBecomesExactAndZeroesTheChange() {
        Order withChange = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals())
                .concluded("000001000", new BigDecimal("6.00"), NOW);
        givenCorrectableOrder(withChange, CashRegisterSession.Status.OPEN,
                capturedLine(1L, PaymentMethod.DINHEIRO, "50.00"));

        OrderUseCase.PaymentCorrectionResult result = orderService.correctPayments(7L, debit("44.00"),
                "foi débito", "ana", false);

        assertThat(result.order().changeAmount()).isNull();
    }

    @Test
    void correctPayments_refusesASumDifferentFromTotalPayable_evenInCash() {
        givenCorrectableOrder(concludedBalcao(), CashRegisterSession.Status.OPEN,
                capturedLine(1L, PaymentMethod.PIX, "44.00"));

        assertThatThrownBy(() -> orderService.correctPayments(7L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("50.00"), null, null, null)),
                "foi dinheiro", "ana", false))
                .isInstanceOf(PaymentTotalMismatchException.class);
        verify(orderPaymentRepository, never()).save(any());
    }

    @Test
    void correctPayments_requiresAReason() {
        assertThatThrownBy(() -> orderService.correctPayments(7L, debit("44.00"), "  ", "ana", false))
                .isInstanceOf(CorrectionReasonRequiredException.class);
    }

    @Test
    void correctPayments_refusesRefundedOrder() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(
                concludedBalcao().refunded("devolução", NOW)));

        assertThatThrownBy(() -> orderService.correctPayments(7L, debit("44.00"), "x", "ana", false))
                .isInstanceOf(OrderNotCorrectableException.class);
    }

    @Test
    void correctPayments_refusesGatewayPaidOrder() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(paidMarketplace()));
        when(orderPaymentRepository.findByOrderId(7L)).thenReturn(List.of(
                capturedLine(1L, PaymentMethod.GATEWAY_PIX, "44.00")));

        assertThatThrownBy(() -> orderService.correctPayments(7L, debit("44.00"), "x", "ana", false))
                .isInstanceOf(GatewayPaymentNotCorrectableException.class);
    }

    @Test
    void correctPayments_closedSessionWithoutManagerPermission_isRefused() {
        givenCorrectableOrder(concludedBalcao(), CashRegisterSession.Status.CLOSED,
                capturedLine(1L, PaymentMethod.PIX, "44.00"));

        assertThatThrownBy(() -> orderService.correctPayments(7L, debit("44.00"), "x", "ana", false))
                .isInstanceOf(CashSessionClosedForCorrectionException.class);
        verify(correctionRepository, never()).save(any());
    }

    @Test
    void correctPayments_closedSessionWithManagerPermission_recordsTheDivergencePerMethod() {
        Order withChange = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals())
                .concluded("000001000", new BigDecimal("6.00"), NOW);
        givenCorrectableOrder(withChange, CashRegisterSession.Status.CLOSED,
                capturedLine(1L, PaymentMethod.DINHEIRO, "50.00"));

        orderService.correctPayments(7L, debit("44.00"), "foi débito", "gerente", true);

        ArgumentCaptor<CashSessionAdjustment> adjustments = ArgumentCaptor.forClass(CashSessionAdjustment.class);
        verify(correctionRepository, times(2)).saveAdjustment(adjustments.capture());
        // A gaveta tinha ficado com 50 − 6 de troco = 44 em dinheiro; agora são 44 no débito.
        assertThat(adjustments.getAllValues())
                .extracting(CashSessionAdjustment::method, a -> a.deltaAmount().stripTrailingZeros())
                .containsExactlyInAnyOrder(
                        tuple(PaymentMethod.DINHEIRO, new BigDecimal("-44")),
                        tuple(PaymentMethod.DEBITO, new BigDecimal("44")));
    }

    @Test
    void getPaymentHistory_splitsLinesByWhoRetiredAndWhoCreatedThem() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(concludedBalcao()));
        OrderPaymentCorrection first = new OrderPaymentCorrection(30L, 7L, "a", "ana", NOW, 1L, false);
        OrderPaymentCorrection second = new OrderPaymentCorrection(31L, 7L, "b", "ana", NOW, 1L, false);
        when(correctionRepository.findByOrderId(7L)).thenReturn(List.of(first, second));
        OrderPayment original = capturedLine(1L, PaymentMethod.PIX, "44.00").corrected(30L, "ana", NOW);
        OrderPayment firstFix = OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("44.00"),
                null, null, null, 30L).corrected(31L, "ana", NOW);
        OrderPayment secondFix = OrderPayment.captured(7L, PaymentMethod.DEBITO, new BigDecimal("44.00"),
                null, null, null, 31L);
        when(orderPaymentRepository.findByOrderId(7L)).thenReturn(List.of(original, firstFix, secondFix));

        List<OrderUseCase.PaymentCorrectionEntry> history = orderService.getPaymentHistory(7L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).before()).containsExactly(original);
        assertThat(history.get(0).after()).containsExactly(firstFix);
        assertThat(history.get(1).before()).containsExactly(firstFix);
        assertThat(history.get(1).after()).containsExactly(secondFix);
    }

    @Test
    void getPaymentHistory_isEmptyWithoutCorrections() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(concludedBalcao()));
        when(correctionRepository.findByOrderId(7L)).thenReturn(List.of());

        assertThat(orderService.getPaymentHistory(7L)).isEmpty();
    }
}
