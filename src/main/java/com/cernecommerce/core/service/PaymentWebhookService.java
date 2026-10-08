package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PaymentWebhookUseCase;
import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Processa notificações de pagamento do gateway (ECM-F004, Fatia 10) — hoje InfinitePay.
 *
 * <p>Separado de {@link ShopService} de propósito: não é superfície de cliente autenticado, é a
 * confirmação assíncrona do gateway, sem sessão nem propriedade a checar.</p>
 *
 * <h2>Defesa sem assinatura de webhook</h2>
 * <p>O InfinitePay não assina suas notificações (confirmado contra a documentação pública do
 * provider — não é uma omissão deste código). A defesa inteira é: (1) só reconsultar o gateway
 * quando já existe, no nosso banco, um pagamento {@code PENDING} para o {@code orderNsu}
 * informado — uma notificação forjada para um pedido sem cobrança pendente nunca chega a gerar
 * uma chamada externa; (2) nunca decidir a partir do corpo da notificação — o valor pago só vale
 * o que {@link PaymentGatewayPort#checkPayment} confirma.</p>
 */
public class PaymentWebhookService implements PaymentWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private static final String WEBHOOK_ACTOR = "webhook:infinitepay";

    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final EstoqueUseCase estoqueUseCase;
    private final CashbackUseCase cashbackUseCase;

    public PaymentWebhookService(OrderRepository orderRepository, OrderPaymentRepository orderPaymentRepository,
            PaymentGatewayPort paymentGatewayPort, EstoqueUseCase estoqueUseCase, CashbackUseCase cashbackUseCase) {
        this.orderRepository = orderRepository;
        this.orderPaymentRepository = orderPaymentRepository;
        this.paymentGatewayPort = paymentGatewayPort;
        this.estoqueUseCase = estoqueUseCase;
        this.cashbackUseCase = cashbackUseCase;
    }

    @Override
    @Transactional
    public WebhookResult handleNotification(String orderNsu, String transactionNsu, String invoiceSlug) {
        Long orderId = parseOrderId(orderNsu);
        if (orderId == null) {
            log.warn("payment.webhook.unknown_order_nsu orderNsu={}", orderNsu);
            return WebhookResult.noop();
        }
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            log.warn("payment.webhook.order_not_found orderId={}", orderId);
            return WebhookResult.noop();
        }

        // Guarda barata, sem chamada externa: sem pagamento PENDING para este pedido, a
        // notificação é retentativa de algo já processado, ou nunca existiu — não há o que fazer,
        // e é justamente esta checagem que substitui a assinatura que o gateway não oferece.
        OrderPayment pending = findPendingPayment(orderId);
        if (pending == null) {
            log.info("payment.webhook.no_pending_payment orderId={}", orderId);
            return WebhookResult.noop();
        }

        // Reconsulta o gateway pela verdade — o valor pago do corpo da notificação nunca decide
        // nada sozinho.
        PaymentGatewayPort.PaymentCheckResult result =
                paymentGatewayPort.checkPayment(orderNsu, transactionNsu, invoiceSlug);

        Order order = orderOpt.get();
        if (!result.paid()) {
            log.info("payment.webhook.not_paid orderId={}", orderId);
            // O pagamento continua PENDING (o cliente pode tentar de novo pelo mesmo link); o
            // resultado só avisa quem quiser reagir à recusa (automações de PAGAMENTO_RECUSADO).
            return WebhookResult.declined(orderId, order.orderNumber());
        }
        if (result.paidAmount() == null || result.paidAmount().compareTo(order.netAmount()) < 0) {
            log.warn("payment.webhook.amount_insufficient orderId={} expected={} paidAmount={}",
                    orderId, order.netAmount(), result.paidAmount());
            return WebhookResult.noop();
        }

        orderPaymentRepository.save(pending.confirmCaptured(transactionNsu, Instant.now()));

        if (order.status() != OrderStatus.AGUARDANDO_PAGAMENTO) {
            // Dinheiro confirmado, mas o pedido não está mais no estado que .paid() aceitaria
            // (cancelado, ou já liquidado por outro caminho entre o checkout e este webhook).
            // O pagamento fica CAPTURED — o dinheiro chegou de verdade — mas o pedido não é
            // tocado; reconciliação manual, não resolvido automaticamente aqui.
            log.error("payment.webhook.order_not_awaiting_payment orderId={} status={}", orderId, order.status());
            return WebhookResult.noop();
        }

        estoqueUseCase.consumeReservationsByOwner(Order.reservationOwnerReference(orderId), WEBHOOK_ACTOR);
        Order paid = orderRepository.save(order.paid(Instant.now()));
        cashbackUseCase.recordEarnedForOrder(paid);

        return new WebhookResult(true, paid.id(), paid.orderNumber());
    }

    private OrderPayment findPendingPayment(Long orderId) {
        List<OrderPayment> payments = orderPaymentRepository.findByOrderId(orderId);
        for (OrderPayment payment : payments) {
            if (payment.status() == PaymentStatus.PENDING) {
                return payment;
            }
        }
        return null;
    }

    private Long parseOrderId(String orderNsu) {
        try {
            return orderNsu == null ? null : Long.valueOf(orderNsu);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
