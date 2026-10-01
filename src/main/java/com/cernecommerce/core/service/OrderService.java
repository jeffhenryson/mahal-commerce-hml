package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;

import com.cernecommerce.core.domain.exception.pagamento.CashSessionClosedForCorrectionException;
import com.cernecommerce.core.domain.exception.pagamento.CorrectionReasonRequiredException;
import com.cernecommerce.core.domain.exception.pagamento.GatewayPaymentNotCorrectableException;
import com.cernecommerce.core.domain.exception.pagamento.InvalidCorrectionPaymentMethodException;
import com.cernecommerce.core.domain.exception.pagamento.OrderNotCorrectableException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentTotalMismatchException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.OrderDeliveryNotEditableException;
import com.cernecommerce.core.domain.exception.pedido.OrderHasNoDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.CashSessionAdjustment;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.OrderPaymentCorrection;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.OrderFilter;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentCorrectionRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.EnumSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OrderService implements OrderUseCase {

    private final OrderRepository orderRepository;
    private final EstoqueUseCase estoqueUseCase;
    private final OrderPaymentRepository orderPaymentRepository;
    private final CashbackUseCase cashbackUseCase;
    private final CashRegisterRepository cashRegisterRepository;
    private final OrderPaymentCorrectionRepository correctionRepository;
    private final ReceivableUseCase receivableUseCase;

    public OrderService(OrderRepository orderRepository, EstoqueUseCase estoqueUseCase,
            OrderPaymentRepository orderPaymentRepository, CashbackUseCase cashbackUseCase,
            CashRegisterRepository cashRegisterRepository, OrderPaymentCorrectionRepository correctionRepository,
            ReceivableUseCase receivableUseCase) {
        this.receivableUseCase = receivableUseCase;
        this.orderRepository = orderRepository;
        this.estoqueUseCase = estoqueUseCase;
        this.orderPaymentRepository = orderPaymentRepository;
        this.cashbackUseCase = cashbackUseCase;
        this.cashRegisterRepository = cashRegisterRepository;
        this.correctionRepository = correctionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listOrders(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size) {
        return orderRepository.findAll(channel, status, customerId, from, to, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listOrders(OrderFilter filter, int page, int size) {
        return orderRepository.findAll(filter, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<PaymentMethod>> getCapturedPaymentMethods(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        return orderPaymentRepository.findCapturedMethodsByOrderIds(orderIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private Order getOrderForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPayment> getOrderPayments(Long orderId) {
        return orderPaymentRepository.findByOrderId(orderId);
    }

    @Override
    @Transactional
    public Order changeStatus(Long orderId, OrderStatus newStatus, String username) {
        // A validação da transição mora em OrderStatus/Order e já tem teste — aqui só se orquestra.
        // RESERVADO -> CONCLUIDO (retirada, PDV-F008) usa pickedUp() em vez do withStatus genérico,
        // porque só pickedUp() carimba concludedAt — withStatus não grava timestamp nenhum (é o
        // caminho da esteira SEPARADO/ENVIADO/ENTREGUE, que hoje mesmo não tem timestamp por etapa).
        Order order = getOrder(orderId);
        boolean pickup = order.status() == OrderStatus.RESERVADO && newStatus == OrderStatus.CONCLUIDO;
        // PED-C006 — este é o caminho da esteira, não o de qualquer transição da tabela. A tabela de
        // OrderStatus deixa AGUARDANDO_PAGAMENTO ir a PAGO/CONCLUIDO, mas só o webhook e o
        // settleOnlineOrder podem fazê-lo: são eles que gravam o pagamento, numeram o pedido e
        // consomem a reserva. Por aqui o pedido do app virava pago sem dinheiro nenhum, e a reserva
        // expirava devolvendo ao saldo a mercadoria já contada como vendida. Reembolso e
        // cancelamento também têm rota própria (e estouravam IllegalArgumentException no construtor).
        if (!pickup && !FULFILLMENT_TARGETS.contains(newStatus)) {
            Set<OrderStatus> allowedHere = order.allowedTransitions().stream()
                    .filter(s -> FULFILLMENT_TARGETS.contains(s)
                            || (s == OrderStatus.CONCLUIDO && order.status() == OrderStatus.RESERVADO))
                    .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(OrderStatus.class)));
            throw new InvalidOrderStatusTransitionException(orderId, order.status(), newStatus, allowedHere);
        }
        Order updated = pickup ? order.pickedUp(Instant.now()) : order.withStatus(newStatus);
        return orderRepository.save(updated);
    }

    /** PED-C006 — os destinos que {@link #changeStatus} aceita, além da retirada da reserva. */
    private static final Set<OrderStatus> FULFILLMENT_TARGETS =
            EnumSet.of(OrderStatus.SEPARADO, OrderStatus.ENVIADO, OrderStatus.ENTREGUE);

    @Override
    @Transactional
    public Order updateDelivery(Long orderId, OrderDelivery.Patch patch, String username) {
        Order order = getOrder(orderId);
        if (order.delivery() == null) {
            throw new OrderHasNoDeliveryException(orderId);
        }
        if (order.status() == OrderStatus.CANCELADO || order.status() == OrderStatus.REEMBOLSADO) {
            throw new OrderDeliveryNotEditableException(orderId, order.status());
        }
        return orderRepository.save(order.withDelivery(order.delivery().withPatch(patch)));
    }

    @Override
    @Transactional
    public Order cancelOrder(Long orderId, String reason, String username) {
        Order order = getOrder(orderId);

        // cancelled() valida a transição (só sai de estado pré-pagamento) e recusa cancelar duas
        // vezes. Chamá-lo ANTES de mexer na reserva é o que impede um segundo cancelamento de
        // liberá-la de novo.
        Order cancelled = order.cancelled(reason, Instant.now());

        // Pré-pagamento nunca teve baixa real de estoque — só reserva (ou nem isso, no balcão, que
        // nunca reserva). Devolver com adjustStock(ENTRADA) aqui inflaria o físico com mercadoria
        // que nunca saiu; releaseReservationsByOwner já é idempotente/zero-safe por design.
        estoqueUseCase.releaseReservationsByOwner(Order.reservationOwnerReference(orderId), username);
        return orderRepository.save(cancelled);
    }

    @Override
    @Transactional
    public Order refundOrder(Long orderId, String reason, String username) {
        return refundOrder(orderId, reason, username, List.of());
    }

    @Override
    @Transactional
    public Order refundOrder(Long orderId, String reason, String username, List<RefundItemLot> itemLots) {
        // PED-C011 — travado: decide sobre as linhas de pagamento, como a correção.
        Order order = getOrderForUpdate(orderId);

        // refunded() valida a transição (só sai de estado pós-pagamento) e recusa reembolsar duas
        // vezes. Chamá-lo ANTES de mexer em estoque/pagamento/cashback é o que impede um segundo
        // reembolso de estornar tudo de novo.
        Order refunded = order.refunded(reason, Instant.now());

        // Casamento por sku: pedido com duas linhas do mesmo SKU recebem a mesma info de lote.
        Map<String, RefundItemLot> lotBySku = itemLots.stream()
                .collect(Collectors.toMap(RefundItemLot::sku, Function.identity(), (first, second) -> first));

        // Devolução é entrada de estoque legítima, inclusive para pedido já entregue. O motivo
        // carrega o número do pedido: sem ele, a trilha do movimento não é reconstruível.
        String movementReason = "Reembolso do pedido " + order.orderNumber();
        // PED-C007 — pedido de MESA não devolve nada: o consumo foi servido e queimado. A essência de
        // lata consumiu USO, não unidade (EST-F027), e o OrderItem não carrega o contador; cortesia e
        // TROCA também voltariam como mercadoria. É a mesma assimetria de ComandaService.undoStock.
        // Reposição real, se houver (bebida fechada devolvida), é ajuste manual de estoque.
        List<OrderItem> returnable = order.channel() == SalesChannel.MESA ? List.of() : order.items();
        for (OrderItem item : returnable) {
            // PDV-F021 — sessão do cardápio não saiu do estoque (SKU sintético): nada a devolver.
            if (!item.mode().isCatalogLine()) {
                continue;
            }
            RefundItemLot lot = lotBySku.get(item.sku());
            // Sempre a sobrecarga de 8 argumentos (EST-F008): sem entrada em lotBySku, lotCode/
            // expiryDate chegam nulos e o comportamento é idêntico ao overload antigo — válido só
            // se o SKU não for lote-rastreado (senão adjustStock lança MissingLotInfoException).
            estoqueUseCase.adjustStock(item.sku(), order.warehouseCode(), MovementType.ENTRADA,
                    item.quantity(), movementReason, username,
                    lot == null ? null : lot.lotCode(), lot == null ? null : lot.expiryDate());
        }

        // Cada pagamento CAPTURED ganha uma linha REFUNDED do mesmo valor — nunca um update, pelo
        // mesmo motivo do resto do ledger. Pagamento que nunca chegou a ser capturado (FAILED,
        // PENDING) não tem o que estornar.
        for (OrderPayment payment : orderPaymentRepository.findByOrderId(orderId)) {
            if (payment.status() == PaymentStatus.CAPTURED) {
                orderPaymentRepository.save(OrderPayment.refunded(payment));
            }
        }

        // Reverte só os ganhos EARNED ainda não revertidos. Cashback resgatado (fatia futura de
        // resgate) não é desfeito por aqui.
        cashbackUseCase.reverseEarningsForOrder(order);

        // CRM-F010 — devolvida a mercadoria, o marcado em aberto do pedido deixa de ser devido.
        receivableUseCase.cancelOpenForOrder(orderId, "Pedido reembolsado: " + reason, username);

        return orderRepository.save(refunded);
    }

    // ── PDV-F030: correção da forma de pagamento ────────────────────────────────────────────

    @Override
    @Transactional
    public PaymentCorrectionResult correctPayments(Long orderId, List<PaymentCommand> payments,
            String reason, String username, boolean canCorrectClosed) {
        if (reason == null || reason.isBlank()) {
            throw new CorrectionReasonRequiredException();
        }
        // PED-C011 — travado: um reembolso em paralelo estornaria a linha que esta correção aposenta
        // e deixaria a linha nova CAPTURED num pedido REEMBOLSADO.
        Order order = getOrderForUpdate(orderId);
        if (order.status() == OrderStatus.CANCELADO || order.status() == OrderStatus.REEMBOLSADO) {
            throw new OrderNotCorrectableException(orderId, "pedido " + order.status());
        }
        List<OrderPayment> current = orderPaymentRepository.findByOrderId(orderId);
        // Pago pelo app: o webhook confirmou, não houve gaveta. Sem sessão não há caixa a reconciliar.
        // Só GATEWAY_PIX capturado bloqueia — o pedido do app pago no balcão (PDV-C015) tem a
        // cobrança de gateway CANCELLED e o dinheiro de fato passou pelo caixa.
        if (order.sessionId() == null || current.stream().anyMatch(p ->
                p.method() == PaymentMethod.GATEWAY_PIX && p.status() == PaymentStatus.CAPTURED)) {
            throw new GatewayPaymentNotCorrectableException(orderId);
        }
        List<OrderPayment> before = current.stream()
                .filter(p -> p.status() == PaymentStatus.CAPTURED)
                .toList();
        if (before.isEmpty()) {
            throw new OrderNotCorrectableException(orderId, "sem pagamento capturado");
        }

        // Valor exato, sem troco: o troco já foi devolvido na venda. Nem DINHEIRO pode exceder.
        // CRM-F010 — a parte MARCADA não foi paga e não se corrige por aqui: o alvo é o que entrou.
        BigDecimal onAccount = current.stream()
                .filter(p -> p.status() == PaymentStatus.ON_ACCOUNT)
                .map(OrderPayment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal target = order.totalPayable().subtract(onAccount);
        BigDecimal informed = BigDecimal.ZERO;
        for (PaymentCommand payment : payments) {
            if (payment.method() == PaymentMethod.GATEWAY_PIX || payment.method() == PaymentMethod.MARCADO) {
                throw new InvalidCorrectionPaymentMethodException(payment.method());
            }
            informed = informed.add(payment.amount());
        }
        if (informed.compareTo(target) != 0) {
            throw new PaymentTotalMismatchException(informed, target);
        }

        CashRegisterSession session = cashRegisterRepository.findById(order.sessionId())
                .orElseThrow(() -> new CashRegisterSessionNotFoundException(order.sessionId()));
        boolean sessionClosed = !session.isOpen();
        if (sessionClosed && !canCorrectClosed) {
            throw new CashSessionClosedForCorrectionException(session.id());
        }

        Instant now = Instant.now();
        OrderPaymentCorrection correction = correctionRepository.save(new OrderPaymentCorrection(null,
                orderId, reason.trim(), username, now, session.id(), sessionClosed));

        List<OrderPayment> retired = new ArrayList<>(before.size());
        for (OrderPayment payment : before) {
            retired.add(orderPaymentRepository.save(payment.corrected(correction.id(), username, now)));
        }
        List<OrderPayment> after = new ArrayList<>(payments.size());
        for (PaymentCommand payment : payments) {
            after.add(orderPaymentRepository.save(OrderPayment.captured(orderId, payment.method(),
                    payment.amount(), payment.installments(), payment.channel(), payment.provider(),
                    correction.id())));
        }

        BigDecimal oldChange = order.changeAmount() == null ? BigDecimal.ZERO : order.changeAmount();
        Order saved = oldChange.signum() > 0
                ? orderRepository.save(order.withChangeAmount(null))
                : order;

        if (sessionClosed) {
            recordClosedSessionAdjustments(session.id(), orderId, correction.id(), before, after, oldChange,
                    username, now);
        }
        return new PaymentCorrectionResult(saved, correction, retired, after);
    }

    /**
     * O esperado de um caixa fechado não é reescrito: o que mudou vira um delta por método. Em
     * DINHEIRO o "antes" é líquido do troco — foi isso que ficou na gaveta.
     */
    private void recordClosedSessionAdjustments(Long sessionId, Long orderId, Long correctionId,
            List<OrderPayment> before, List<OrderPayment> after, BigDecimal oldChange, String username,
            Instant now) {
        Map<PaymentMethod, BigDecimal> delta = new EnumMap<>(PaymentMethod.class);
        for (OrderPayment p : before) {
            delta.merge(p.method(), p.amount().negate(), BigDecimal::add);
        }
        if (oldChange.signum() > 0) {
            delta.merge(PaymentMethod.DINHEIRO, oldChange, BigDecimal::add);
        }
        for (OrderPayment p : after) {
            delta.merge(p.method(), p.amount(), BigDecimal::add);
        }
        delta.forEach((method, amount) -> {
            if (amount.signum() != 0) {
                correctionRepository.saveAdjustment(new CashSessionAdjustment(null, sessionId, orderId,
                        correctionId, method, amount, username, now));
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentCorrectionEntry> getPaymentHistory(Long orderId) {
        getOrder(orderId);
        List<OrderPaymentCorrection> corrections = correctionRepository.findByOrderId(orderId);
        if (corrections.isEmpty()) {
            return List.of();
        }
        List<OrderPayment> payments = orderPaymentRepository.findByOrderId(orderId);
        return corrections.stream()
                .map(c -> new PaymentCorrectionEntry(c,
                        payments.stream().filter(p -> c.id().equals(p.correctionId())).toList(),
                        payments.stream().filter(p -> c.id().equals(p.originCorrectionId())).toList()))
                .toList();
    }
}
