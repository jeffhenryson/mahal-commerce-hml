package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PaymentWebhookUseCase.WebhookResult;
import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock PaymentGatewayPort paymentGatewayPort;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CashbackUseCase cashbackUseCase;

    PaymentWebhookService service;

    private static final Long ORDER_ID = 99L;
    private static final Long CUSTOMER_ID = 7L;

    @BeforeEach
    void setUp() {
        service = new PaymentWebhookService(orderRepository, orderPaymentRepository, paymentGatewayPort,
                estoqueUseCase, cashbackUseCase);
    }

    private Order marketplaceOrder() {
        OrderItem item = OrderItem.fromCatalog("ESS-001", BigDecimal.ONE,
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")), BigDecimal.ZERO);
        Order order = Order.openMarketplace(CUSTOMER_ID, "LOJA-01", List.of(item));
        return Order.of(ORDER_ID, order.orderNumber(), order.channel(), order.status(), order.customerId(),
                order.sessionId(), order.warehouseCode(), order.items(), order.grossAmount(),
                order.discountAmount(), order.cashbackRedeemed(), order.netAmount(), order.changeAmount(),
                order.cancelReason(), order.createdAt(), order.paidAt(), order.concludedAt(),
                order.cancelledAt(), order.refundedAt(), order.version());
    }

    private OrderPayment pendingPayment() {
        return OrderPayment.of(5L, ORDER_ID, PaymentMethod.GATEWAY_PIX, new BigDecimal("30.00"),
                PaymentStatus.PENDING, null, null, null, null, java.time.Instant.now());
    }

    @Test
    void handleNotification_unparseableOrderNsuIsNoOpWithoutTouchingTheGateway() {
        WebhookResult result = service.handleNotification("not-a-number", "txn-1", "slug-1");

        assertThat(result.orderPaid()).isFalse();
        verifyNoInteractions(paymentGatewayPort);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void handleNotification_unknownOrderIsNoOpWithoutTouchingTheGateway() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        WebhookResult result = service.handleNotification(ORDER_ID.toString(), "txn-1", "slug-1");

        assertThat(result.orderPaid()).isFalse();
        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    void handleNotification_noPendingPaymentIsNoOpWithoutTouchingTheGateway() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(marketplaceOrder()));
        when(orderPaymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        WebhookResult result = service.handleNotification(ORDER_ID.toString(), "txn-1", "slug-1");

        assertThat(result.orderPaid()).isFalse();
        // Esta é a defesa central contra spam: sem PENDING, a notificação nunca chega a
        // reconsultar o gateway — nem uma notificação forjada custa uma chamada externa.
        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    void handleNotification_notPaidYetIsNoOpAndDoesNotCapture() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(marketplaceOrder()));
        when(orderPaymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(pendingPayment()));
        when(paymentGatewayPort.checkPayment(ORDER_ID.toString(), "txn-1", "slug-1"))
                .thenReturn(new PaymentGatewayPort.PaymentCheckResult(false, null));

        WebhookResult result = service.handleNotification(ORDER_ID.toString(), "txn-1", "slug-1");

        assertThat(result.orderPaid()).isFalse();
        assertThat(result.paymentDeclined()).isTrue();
        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        verify(orderPaymentRepository, never()).save(any());
        verifyNoInteractions(estoqueUseCase, cashbackUseCase);
    }

    @Test
    void handleNotification_paidButAmountBelowExpectedDoesNotCapture() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(marketplaceOrder()));
        when(orderPaymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(pendingPayment()));
        when(paymentGatewayPort.checkPayment(ORDER_ID.toString(), "txn-1", "slug-1"))
                .thenReturn(new PaymentGatewayPort.PaymentCheckResult(true, new BigDecimal("10.00")));

        WebhookResult result = service.handleNotification(ORDER_ID.toString(), "txn-1", "slug-1");

        assertThat(result.orderPaid()).isFalse();
        verify(orderPaymentRepository, never()).save(any());
    }

    @Test
    void handleNotification_happyPathConsumesReservationMarksOrderPaidAndRecordsCashback() {
        Order order = marketplaceOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderPaymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(pendingPayment()));
        when(paymentGatewayPort.checkPayment(ORDER_ID.toString(), "txn-1", "slug-1"))
                .thenReturn(new PaymentGatewayPort.PaymentCheckResult(true, order.netAmount()));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebhookResult result = service.handleNotification(ORDER_ID.toString(), "txn-1", "slug-1");

        assertThat(result.orderPaid()).isTrue();
        assertThat(result.orderId()).isEqualTo(ORDER_ID);

        var inOrder = inOrder(orderPaymentRepository, estoqueUseCase, orderRepository, cashbackUseCase);
        inOrder.verify(orderPaymentRepository).save(argThat(p ->
                p.status() == PaymentStatus.CAPTURED && "txn-1".equals(p.gatewayRef())));
        inOrder.verify(estoqueUseCase).consumeReservationsByOwner(eq("ORDER:99"), anyString());
        inOrder.verify(orderRepository).save(argThat(o -> o.status() == OrderStatus.PAGO));
        inOrder.verify(cashbackUseCase).recordEarnedForOrder(any());
    }

    @Test
    void handleNotification_orderNoLongerAwaitingPaymentStillCapturesButDoesNotTouchOrder() {
        Order order = marketplaceOrder().paid(java.time.Instant.now()); // já PAGO por outro caminho
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderPaymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(pendingPayment()));
        when(paymentGatewayPort.checkPayment(ORDER_ID.toString(), "txn-1", "slug-1"))
                .thenReturn(new PaymentGatewayPort.PaymentCheckResult(true, order.netAmount()));

        WebhookResult result = service.handleNotification(ORDER_ID.toString(), "txn-1", "slug-1");

        assertThat(result.orderPaid()).isFalse();
        verify(orderPaymentRepository).save(argThat(p -> p.status() == PaymentStatus.CAPTURED));
        verify(estoqueUseCase, never()).consumeReservationsByOwner(any(), any());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(cashbackUseCase);
    }
}
