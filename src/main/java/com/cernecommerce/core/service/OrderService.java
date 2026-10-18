package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;

import com.cernecommerce.core.domain.exception.pagamento.CashSessionClosedForCorrectionException;
import com.cernecommerce.core.domain.exception.pagamento.CorrectionReasonRequiredException;
import com.cernecommerce.core.domain.exception.pagamento.GatewayPaymentNotCorrectableException;
import com.cernecommerce.core.domain.exception.pagamento.InvalidCorrectionPaymentMethodException;
import com.cernecommerce.core.domain.exception.pagamento.OrderNotCorrectableException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentTotalMismatchException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.CashRefundRequiresOpenSessionException;
import com.cernecommerce.core.domain.exception.pedido.OrderDeliveryNotEditableException;
import com.cernecommerce.core.domain.exception.pedido.OrderHasNoDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.ports.out.pedido.OrderItemRefundRepository;
import com.cernecommerce.core.ports.out.pedido.OrderItemExchangeRepository;
import com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException;
import com.cernecommerce.core.domain.exception.pedido.ExchangeItemInvalidException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotExchangeableException;
import com.cernecommerce.core.domain.exception.pedido.SettlementMethodRequiredException;
import com.cernecommerce.core.domain.model.pedido.OrderItemExchange;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.pedido.RefundDestination;
import com.cernecommerce.core.domain.model.pedido.OrderItemRefund;
import com.cernecommerce.core.domain.model.pedido.RefundPlanLine;
import com.cernecommerce.core.domain.model.pagamento.RefundPot;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.exception.pedido.RefundQuantityExceededException;
import com.cernecommerce.core.domain.exception.pedido.RefundNotOwnSessionException;
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
import java.util.HashMap;
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
    private final OrderItemRefundRepository itemRefundRepository;
    private final OrderItemExchangeRepository itemExchangeRepository;

    public OrderService(OrderRepository orderRepository, EstoqueUseCase estoqueUseCase,
            OrderPaymentRepository orderPaymentRepository, CashbackUseCase cashbackUseCase,
            CashRegisterRepository cashRegisterRepository, OrderPaymentCorrectionRepository correctionRepository,
            ReceivableUseCase receivableUseCase, OrderItemRefundRepository itemRefundRepository,
            OrderItemExchangeRepository itemExchangeRepository) {
        this.receivableUseCase = receivableUseCase;
        this.itemRefundRepository = itemRefundRepository;
        this.itemExchangeRepository = itemExchangeRepository;
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
        return refundOrder(orderId, new RefundCommand(reason, true, itemLots, List.of()), username, false);
    }

    // ── PED-F004: estorno total ou por item ─────────────────────────────────────────────────

    @Override
    @Transactional
    public Order refundOrder(Long orderId, RefundCommand command, String username, boolean ownSessionOnly) {
        // PED-C011 — travado: decide sobre as linhas de pagamento, como a correção.
        Order order = getOrderForUpdate(orderId);
        String reason = command.reason();

        // A transição é validada ANTES de mexer em estoque/pagamento/cashback: é o que impede um
        // estorno de pedido já REEMBOLSADO (ou ainda não pago) de estornar tudo de novo. O estorno
        // parcial não muda o status, mas exige o mesmo estado de origem.
        if (!order.allowedTransitions().contains(OrderStatus.REEMBOLSADO)) {
            throw new InvalidOrderStatusTransitionException(orderId, order.status(), OrderStatus.REEMBOLSADO,
                    order.allowedTransitions());
        }

        // O estorno sai da gaveta de quem estorna. Sem caixa aberto, só estorno que não devolve
        // dinheiro (PED-C009), e a soma cai no caixa do pedido.
        Long refundSessionId = cashRegisterRepository.findOpenByOperator(username)
                .map(CashRegisterSession::id).orElse(null);
        if (ownSessionOnly && (refundSessionId == null || !refundSessionId.equals(order.sessionId()))) {
            throw new RefundNotOwnSessionException(orderId, username);
        }
        if (refundSessionId != null) {
            // PED-C013 — mesma trava compartilhada de toda escrita que soma no esperado (PDV-C035).
            // Sem ela, um fechamento simultâneo gravava o esperado e o REFUNDED caía no caixa já
            // fechado: a cédula saía e a conferência não a contava.
            cashRegisterRepository.lockForShare(refundSessionId);
            boolean stillOpen = cashRegisterRepository.findById(refundSessionId)
                    .map(CashRegisterSession::isOpen).orElse(false);
            if (!stillOpen) {
                throw new CashRegisterSessionClosedException(refundSessionId);
            }
        }
        List<OrderPayment> payments = orderPaymentRepository.findByOrderId(orderId);
        // PED-C009 — sem caixa aberto o estorno em dinheiro cairia no caixa do pedido, talvez fechado
        // há dias. Decisão do dono (2026-10-17): devolver cédula exige gaveta aberta de quem devolve.
        if (refundSessionId == null && hasCashLeftToRefund(payments)) {
            throw new CashRefundRequiresOpenSessionException(orderId, username);
        }

        Map<Long, BigDecimal> already = itemRefundRepository.sumQuantityByOrderItem(orderId);
        List<RefundPlanLine> lines = command.items().isEmpty()
                ? remainingLines(order, already,
                        command.restock() ? RefundDestination.RESTOCK : RefundDestination.CUSTOMER_KEEPS)
                : requestedLines(order, already, command.items());
        Map<Integer, BigDecimal> planned = plannedByIndex(lines);
        boolean completes = completes(order, already, planned);
        Instant now = Instant.now();

        returnToStock(order, lines, command.itemLots(), completes, username);

        BigDecimal requested = lines.stream().map(RefundPlanLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        refundPayments(orderId, payments, completes, requested, refundSessionId, reason, username);

        // O ledger reverte uma entrada EARNED inteira: no parcial, só a do item que zerou agora.
        if (completes) {
            cashbackUseCase.reverseEarningsForOrder(order);
        } else {
            cashbackUseCase.reverseEarningsForOrderItems(order, zeroedItems(order, already, planned));
        }

        itemRefundRepository.saveAll(lines.stream()
                .map(l -> OrderItemRefund.register(orderId, l.item().id(), l.item().sku(), l.quantity(),
                        l.destination(), l.amount(), costOf(l.item(), l.quantity()), reason, refundSessionId,
                        username, now))
                .toList());

        if (!completes) {
            return order;
        }
        // CRM-F010 — devolvida a mercadoria, o marcado em aberto do pedido deixa de ser devido.
        receivableUseCase.cancelOpenForOrder(orderId, "Pedido reembolsado: " + reason, username);
        return orderRepository.save(order.refunded(reason, now));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getRefundedQuantities(Long orderId) {
        return itemRefundRepository.sumQuantityByOrderItem(orderId);
    }

    // ── PED-F006: troca de item ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ExchangeResult exchangeItem(Long orderId, ExchangeCommand command, String username) {
        // Travado como o estorno (PED-C011): decide sobre as unidades restantes e as linhas de pagamento.
        Order order = getOrderForUpdate(orderId);
        // Mesmo estado de origem do estorno: pago e ainda não totalmente reembolsado.
        if (!order.allowedTransitions().contains(OrderStatus.REEMBOLSADO)) {
            throw new OrderNotExchangeableException(orderId, order.status());
        }
        // PED-C007 — o consumo do salão foi servido e queimado: não há o que devolver ao balcão.
        if (order.channel() == SalesChannel.MESA) {
            throw new OrderNotExchangeableException(orderId, "pedido de mesa — use o estorno");
        }
        if (command.destination() == null || command.destination() == RefundDestination.CUSTOMER_KEEPS) {
            throw new ExchangeItemInvalidException("O destino da troca é RESTOCK ou DISCARD.");
        }

        // A perna devolvida é o estorno por item: mesmas unidades restantes, mesmo unitário.
        Map<Long, BigDecimal> already = itemRefundRepository.sumQuantityByOrderItem(orderId);
        List<RefundPlanLine> lines;
        try {
            lines = requestedLines(order, already,
                    List.of(new RefundItemCommand(command.sku(), command.quantity(), command.destination())));
        } catch (RefundQuantityExceededException e) {
            throw new ExchangeItemInvalidException("O item " + command.sku() + " não tem " + command.quantity()
                    + " unidade(s) para trocar neste pedido.");
        }
        if (lines.stream().anyMatch(l -> !l.item().mode().isCatalogLine())) {
            throw new ExchangeItemInvalidException("Só item de catálogo pode ser trocado: " + command.sku() + ".");
        }
        BigDecimal returnedAmount = lines.stream().map(RefundPlanLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // O novo item sai pelo preço efetivo de agora, sem desconto. resolveSaleInfo recusa SKU
        // inexistente (404) e base não vendável (PARENT_NOT_SELLABLE); fromCatalog, produto sem preço.
        EstoqueUseCase.CatalogSaleInfo saleInfo = estoqueUseCase.resolveSaleInfo(command.newSku());
        if (saleInfo.sessionProduct()) {
            throw new ExchangeItemInvalidException("O item " + command.newSku() + " é vendido por sessão de mesa.");
        }
        OrderItem newItem = OrderItem.fromCatalog(command.newSku(), command.newQuantity(), saleInfo.pricing(),
                BigDecimal.ZERO, saleInfo.productName());
        BigDecimal newAmount = newItem.netAmount().setScale(Money.MONEY_SCALE, Money.ROUNDING);
        BigDecimal difference = newAmount.subtract(returnedAmount);
        PaymentMethod method = difference.signum() == 0 ? null : command.settlementMethod();
        if (difference.signum() != 0 && method == null) {
            throw new SettlementMethodRequiredException(difference);
        }

        // A diferença a pagar entra no caixa de quem troca; a devolvida em dinheiro sai da gaveta dele.
        Long sessionId = cashRegisterRepository.findOpenByOperator(username)
                .map(CashRegisterSession::id).orElse(null);
        boolean needsDrawer = difference.signum() > 0
                || (difference.signum() < 0 && method == PaymentMethod.DINHEIRO);
        if (needsDrawer && sessionId == null) {
            throw new NoOpenCashRegisterSessionException(username);
        }
        if (sessionId != null) {
            // PED-C013 — mesma trava compartilhada do estorno (PDV-C035).
            cashRegisterRepository.lockForShare(sessionId);
            boolean stillOpen = cashRegisterRepository.findById(sessionId)
                    .map(CashRegisterSession::isOpen).orElse(false);
            if (!stillOpen) {
                throw new CashRegisterSessionClosedException(sessionId);
            }
        }

        // Estoque: a devolvida volta (RESTOCK) ou é perda sem mexer no saldo (DISCARD); a nova sai.
        String movementReason = "Troca no pedido " + order.orderNumber();
        for (RefundPlanLine line : lines) {
            if (line.destination().returnsToStock()) {
                estoqueUseCase.adjustStock(line.item().sku(), order.warehouseCode(), MovementType.ENTRADA,
                        line.quantity(), movementReason, username, null, null);
            }
        }
        estoqueUseCase.adjustStock(command.newSku(), order.warehouseCode(), MovementType.SAIDA,
                command.newQuantity(), movementReason, username);

        Order saved = orderRepository.save(order.withAddedItem(newItem));
        Set<Long> originalIds = order.items().stream().map(OrderItem::id).collect(Collectors.toSet());
        Long newItemId = saved.items().stream().map(OrderItem::id)
                .filter(id -> id != null && !originalIds.contains(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("item da troca não recebeu id"));

        Long paymentId = null;
        if (difference.signum() > 0) {
            paymentId = orderPaymentRepository.save(
                    OrderPayment.capturedInSession(orderId, method, difference, sessionId)).id();
        } else if (difference.signum() < 0) {
            paymentId = orderPaymentRepository.save(
                    OrderPayment.refundedInSession(orderId, method, difference.negate(), sessionId)).id();
        }

        // O ledger reverte uma entrada EARNED inteira: só a do item que zerou agora (como no parcial).
        cashbackUseCase.reverseEarningsForOrderItems(order, zeroedItems(order, already, plannedByIndex(lines)));

        Instant now = Instant.now();
        String reason = command.reason();
        OrderItemExchange exchange = itemExchangeRepository.save(OrderItemExchange.register(orderId,
                lines.getFirst().item().id(), command.sku(), command.quantity(), command.destination(), newItemId,
                command.newSku(), command.newQuantity(), returnedAmount, newAmount, method, paymentId, sessionId,
                reason, username, now));
        itemRefundRepository.saveAll(lines.stream()
                .map(l -> OrderItemRefund.forExchange(exchange.id(), orderId, l.item().id(), l.item().sku(),
                        l.quantity(), l.destination(), l.amount(), costOf(l.item(), l.quantity()), reason, sessionId,
                        username, now))
                .toList());
        return new ExchangeResult(saved, exchange);
    }

    /** Sem {@code items}: tudo o que ainda não foi estornado, com um destino só. */
    private static List<RefundPlanLine> remainingLines(Order order, Map<Long, BigDecimal> already,
            RefundDestination destination) {
        List<RefundPlanLine> lines = new ArrayList<>();
        for (int i = 0; i < order.items().size(); i++) {
            BigDecimal remaining = remainingOf(order, i, already, new HashMap<>());
            if (remaining.signum() > 0) {
                lines.add(line(order, i, remaining, destination));
            }
        }
        return lines;
    }

    /**
     * Com {@code items}: cada pedido de SKU é distribuído pelas linhas daquele SKU, na ordem da venda,
     * até onde cada uma ainda tem unidade. Faltou → 422 antes de qualquer escrita.
     */
    private static List<RefundPlanLine> requestedLines(Order order, Map<Long, BigDecimal> already,
            List<RefundItemCommand> commands) {
        Map<Integer, BigDecimal> planned = new HashMap<>();
        List<RefundPlanLine> lines = new ArrayList<>();
        for (RefundItemCommand command : commands) {
            if (command.sku() == null || command.destination() == null
                    || command.quantity() == null || command.quantity().signum() <= 0) {
                throw new IllegalArgumentException("item de estorno precisa de sku, quantity > 0 e destination");
            }
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < order.items().size(); i++) {
                if (order.items().get(i).sku().equals(command.sku())) {
                    candidates.add(i);
                }
            }
            BigDecimal available = candidates.stream()
                    .map(i -> remainingOf(order, i, already, planned))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (command.quantity().compareTo(available) > 0) {
                throw new RefundQuantityExceededException(command.sku(), command.quantity(), available);
            }
            BigDecimal need = command.quantity();
            for (int i : candidates) {
                if (need.signum() <= 0) {
                    break;
                }
                BigDecimal take = need.min(remainingOf(order, i, already, planned));
                if (take.signum() <= 0) {
                    continue;
                }
                planned.merge(i, take, BigDecimal::add);
                lines.add(line(order, i, take, command.destination()));
                need = need.subtract(take);
            }
        }
        return lines;
    }

    /** Vendidas − já estornadas (por id, do banco) − planejadas neste estorno (por posição). */
    private static BigDecimal remainingOf(Order order, int index, Map<Long, BigDecimal> already,
            Map<Integer, BigDecimal> planned) {
        OrderItem item = order.items().get(index);
        BigDecimal refunded = item.id() == null ? BigDecimal.ZERO : already.getOrDefault(item.id(), BigDecimal.ZERO);
        return item.quantity()
                .subtract(refunded)
                .subtract(planned.getOrDefault(index, BigDecimal.ZERO))
                .max(BigDecimal.ZERO);
    }

    /**
     * PED-C007 — pedido de MESA não devolve nada ao estoque (o consumo foi servido e queimado), nem a
     * sessão do cardápio (PDV-F021, SKU sintético). Nesses casos RESTOCK vale como CUSTOMER_KEEPS, e é
     * o destino efetivo que fica gravado.
     */
    private static RefundPlanLine line(Order order, int index, BigDecimal quantity, RefundDestination requested) {
        OrderItem item = order.items().get(index);
        boolean canRestock = order.channel() != SalesChannel.MESA && item.mode().isCatalogLine();
        RefundDestination destination = requested == RefundDestination.RESTOCK && !canRestock
                ? RefundDestination.CUSTOMER_KEEPS : requested;
        return new RefundPlanLine(index, item, quantity, destination, amountOf(item, quantity));
    }

    /** O líquido proporcional das unidades: {@code netAmount / quantity × unidades}. */
    private static BigDecimal amountOf(OrderItem item, BigDecimal units) {
        BigDecimal net = item.netAmount();
        if (net == null || net.signum() <= 0) {
            return BigDecimal.ZERO.setScale(Money.MONEY_SCALE);
        }
        if (units.compareTo(item.quantity()) == 0) {
            return net;
        }
        return net.multiply(units).divide(item.quantity(), Money.MONEY_SCALE, Money.ROUNDING);
    }

    private static BigDecimal costOf(OrderItem item, BigDecimal units) {
        return item.costPrice() == null ? null
                : item.costPrice().multiply(units).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }

    private static Map<Integer, BigDecimal> plannedByIndex(List<RefundPlanLine> lines) {
        Map<Integer, BigDecimal> planned = new HashMap<>();
        lines.forEach(l -> planned.merge(l.index(), l.quantity(), BigDecimal::add));
        return planned;
    }

    /** Depois deste estorno, nenhuma unidade do pedido fica sem estorno. */
    private static boolean completes(Order order, Map<Long, BigDecimal> already, Map<Integer, BigDecimal> planned) {
        for (int i = 0; i < order.items().size(); i++) {
            if (remainingOf(order, i, already, planned).signum() > 0) {
                return false;
            }
        }
        return true;
    }

    /** Ids dos itens que este estorno zerou agora. */
    private static List<Long> zeroedItems(Order order, Map<Long, BigDecimal> already,
            Map<Integer, BigDecimal> planned) {
        List<Long> zeroed = new ArrayList<>();
        for (int i = 0; i < order.items().size(); i++) {
            OrderItem item = order.items().get(i);
            if (item.id() != null && planned.containsKey(i) && remainingOf(order, i, already, planned).signum() == 0) {
                zeroed.add(item.id());
            }
        }
        return zeroed;
    }

    /**
     * RESTOCK dá ENTRADA no estoque; DISCARD e CUSTOMER_KEEPS não mexem no saldo. Devolução é entrada
     * de estoque legítima, inclusive para pedido já entregue; o motivo carrega o número do pedido para
     * a trilha do movimento ser reconstruível.
     */
    private void returnToStock(Order order, List<RefundPlanLine> lines, List<RefundItemLot> itemLots, boolean completes,
            String username) {
        // Casamento por sku: pedido com duas linhas do mesmo SKU recebem a mesma info de lote.
        Map<String, RefundItemLot> lotBySku = itemLots.stream()
                .collect(Collectors.toMap(RefundItemLot::sku, Function.identity(), (first, second) -> first));
        String movementReason = (completes ? "Reembolso do pedido " : "Estorno parcial do pedido ")
                + order.orderNumber();
        for (RefundPlanLine line : lines) {
            if (!line.destination().returnsToStock()) {
                continue;
            }
            RefundItemLot lot = lotBySku.get(line.item().sku());
            // Sempre a sobrecarga de 8 argumentos (EST-F008): sem lote, lotCode/expiryDate chegam nulos,
            // o que só vale se o SKU não for lote-rastreado (senão MissingLotInfoException).
            estoqueUseCase.adjustStock(line.item().sku(), order.warehouseCode(), MovementType.ENTRADA,
                    line.quantity(), movementReason, username,
                    lot == null ? null : lot.lotCode(), lot == null ? null : lot.expiryDate());
        }
    }

    /**
     * Devolve o dinheiro. Cada estorno é uma linha REFUNDED nova — nunca um update —, na forma de
     * pagamento original e na sessão de quem estorna.
     *
     * <ul>
     *   <li>O estorno que completa o pedido devolve todo o restante de cada pagamento CAPTURED (taxa de
     *       serviço e de entrega incluídas); o marcado é cancelado por quem chama.</li>
     *   <li>O parcial devolve o líquido das unidades, limitado ao que ainda resta, rateado entre as
     *       formas de pagamento pelo peso de cada uma na venda. A parte do marcado abate o recebível.</li>
     * </ul>
     */
    private void refundPayments(Long orderId, List<OrderPayment> payments, boolean completes, BigDecimal requested,
            Long refundSessionId, String reason, String username) {
        // O REFUNDED não aponta a linha de origem: o já estornado de cada método é descontado das
        // linhas CAPTURED daquele método, na ordem em que foram lançadas.
        Map<PaymentMethod, BigDecimal> refundedByMethod = new EnumMap<>(PaymentMethod.class);
        payments.stream().filter(p -> p.status() == PaymentStatus.REFUNDED)
                .forEach(p -> refundedByMethod.merge(p.method(), p.amount(), BigDecimal::add));
        List<RefundPot> pots = new ArrayList<>();
        for (OrderPayment payment : payments) {
            if (payment.status() == PaymentStatus.CAPTURED) {
                BigDecimal used = refundedByMethod.getOrDefault(payment.method(), BigDecimal.ZERO).min(payment.amount());
                refundedByMethod.merge(payment.method(), used.negate(), BigDecimal::add);
                pots.add(new RefundPot(payment, payment.amount(), payment.amount().subtract(used)));
            } else if (payment.status() == PaymentStatus.ON_ACCOUNT && !completes) {
                BigDecimal open = receivableUseCase.findByOrderId(orderId)
                        .filter(r -> r.status().isOpen())
                        .map(CustomerReceivable::amountOpen)
                        .orElse(BigDecimal.ZERO);
                pots.add(new RefundPot(payment, payment.amount(), open));
            }
        }

        if (completes) {
            for (RefundPot pot : pots) {
                if (pot.remaining().signum() > 0) {
                    orderPaymentRepository.save(OrderPayment.refunded(pot.payment(), pot.remaining(), refundSessionId));
                }
            }
            return;
        }

        BigDecimal available = pots.stream().map(RefundPot::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = requested.min(available);
        if (total.signum() <= 0) {
            return;
        }
        List<BigDecimal> shares = RefundPot.allocate(total, pots);
        for (int i = 0; i < pots.size(); i++) {
            BigDecimal share = shares.get(i);
            if (share.signum() <= 0) {
                continue;
            }
            OrderPayment payment = pots.get(i).payment();
            if (payment.status() == PaymentStatus.CAPTURED) {
                orderPaymentRepository.save(OrderPayment.refunded(payment, share, refundSessionId));
            } else {
                receivableUseCase.reduceOpenForOrder(orderId, share, "Estorno parcial: " + reason, username);
            }
        }
    }

    /** PED-C009 — ainda há dinheiro capturado e não estornado neste pedido? */
    private static boolean hasCashLeftToRefund(List<OrderPayment> payments) {
        BigDecimal left = BigDecimal.ZERO;
        for (OrderPayment payment : payments) {
            if (payment.method() != PaymentMethod.DINHEIRO) {
                continue;
            }
            if (payment.status() == PaymentStatus.CAPTURED) {
                left = left.add(payment.amount());
            } else if (payment.status() == PaymentStatus.REFUNDED) {
                left = left.subtract(payment.amount());
            }
        }
        return left.signum() > 0;
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

        // PDV-C035 — "caixa aberto ou fechado" decide onde a diferença vai; sem a trava, um
        // fechamento concorrente faria a correção valer o caixa aberto num esperado já gravado.
        cashRegisterRepository.lockForShare(order.sessionId());
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
