package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pagamento.ChangeNotSupportedException;
import com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentExceedsOrderTotalException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionHasOpenComandasException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionStaleException;
import com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.domain.exception.recebivel.OnAccountNotSupportedException;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class PdvService implements PdvUseCase {

    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository cashMovementRepository;
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final EstoqueUseCase estoqueUseCase;
    private final CashbackUseCase cashbackUseCase;
    /**
     * PDV-C005 — só para barrar o fechamento com mesa aberta. É o <b>port</b>, não o
     * {@code ComandaService}: este service já é dependência daquele, e inverter a seta criaria um
     * ciclo de beans.
     */
    private final ComandaRepository comandaRepository;

    /** Teto de desconto por pedido, em percentual sobre o bruto. */
    private final BigDecimal maxDiscountPercent;

    /** PDV-F022 — "hoje" para barrar venda em caixa de ontem; injetável para teste. */
    private final Clock clock;

    /** Fuso do dia de caixa: é a data da loja que conta, não a do servidor em UTC. */
    static final ZoneId ZONA_LOJA = ZoneId.of("America/Sao_Paulo");

    private final ReceivableUseCase receivableUseCase;

    public PdvService(CashRegisterRepository cashRegisterRepository,
            CashMovementRepository cashMovementRepository, OrderRepository orderRepository,
            OrderPaymentRepository orderPaymentRepository, EstoqueUseCase estoqueUseCase,
            CashbackUseCase cashbackUseCase, ComandaRepository comandaRepository,
            BigDecimal maxDiscountPercent, ReceivableUseCase receivableUseCase) {
        this(cashRegisterRepository, cashMovementRepository, orderRepository, orderPaymentRepository,
                estoqueUseCase, cashbackUseCase, comandaRepository, maxDiscountPercent, Clock.systemUTC(),
                receivableUseCase);
    }

    public PdvService(CashRegisterRepository cashRegisterRepository,
            CashMovementRepository cashMovementRepository, OrderRepository orderRepository,
            OrderPaymentRepository orderPaymentRepository, EstoqueUseCase estoqueUseCase,
            CashbackUseCase cashbackUseCase, ComandaRepository comandaRepository,
            BigDecimal maxDiscountPercent, Clock clock, ReceivableUseCase receivableUseCase) {
        this.clock = clock;
        this.receivableUseCase = receivableUseCase;
        this.cashRegisterRepository = cashRegisterRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.orderRepository = orderRepository;
        this.orderPaymentRepository = orderPaymentRepository;
        this.estoqueUseCase = estoqueUseCase;
        this.cashbackUseCase = cashbackUseCase;
        this.comandaRepository = comandaRepository;
        this.maxDiscountPercent = maxDiscountPercent;
    }

    // ── Ciclo de caixa ───────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> listSessions(int page, int size) {
        return cashRegisterRepository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> listSessions(CashRegisterSessionFilter filter, int page, int size) {
        return cashRegisterRepository.findAll(filter, page, size);
    }

    @Override
    @Transactional
    public CashRegisterSession openSession(String operator, BigDecimal openingAmount, String warehouseCode) {
        // Um caixa por operador. O índice parcial único da V66 é quem garante isto sob concorrência;
        // esta checagem existe para devolver um erro legível em vez de uma violação de constraint.
        cashRegisterRepository.findOpenByOperator(operator).ifPresent(open -> {
            throw new CashRegisterSessionAlreadyOpenException(operator, open.id());
        });
        // Valida o depósito ANTES de carimbá-lo na sessão: descobrir que ele não existe na primeira
        // venda seria descobrir tarde demais, com o cliente no balcão.
        estoqueUseCase.getWarehouseByCode(warehouseCode);
        return cashRegisterRepository.save(
                CashRegisterSession.open(operator, openingAmount, warehouseCode));
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterSession getCurrentSession(String operator) {
        return cashRegisterRepository.findOpenByOperator(operator)
                .orElseThrow(() -> new NoOpenCashRegisterSessionException(operator));
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterSession getSession(Long sessionId) {
        return cashRegisterRepository.findById(sessionId)
                .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
    }

    @Override
    @Transactional
    public CashMovement registerCashMovement(Long sessionId, CashMovementType type, BigDecimal amount,
            String reason, String username) {
        requireOwnOpenSession(sessionId, username);
        return cashMovementRepository.save(
                CashMovement.register(sessionId, type, amount, reason, username));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashMovement> listCashMovements(Long sessionId, int page, int size) {
        getSession(sessionId);
        return cashMovementRepository.findBySessionId(sessionId, page, size);
    }

    @Override
    @Transactional
    public CashRegisterSession closeSession(Long sessionId, BigDecimal countedAmount, String notes,
            String username, boolean canCloseAny) {
        CashRegisterSession session = getSession(sessionId);
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        // Fechar o caixa de OUTRO operador é conferência de gerente (admin/dev), e é por isso que
        // PDV_SESSION_CLOSE existe separada de PDV_SESSION_MANAGE. O atendente também tem
        // PDV_SESSION_CLOSE (V86) para fechar o próprio turno, mas não o do colega: até aqui a API
        // aceitava, e um atendente podia encerrar a gaveta de outro pela rota.
        if (!canCloseAny && !session.belongsTo(username)) {
            throw new CashRegisterSessionNotOwnedException(sessionId, username);
        }
        //
        // PDV-C005: mas mesa aberta barra o fechamento, e esta é a única regra do ciclo de caixa
        // que BLOQUEIA em vez de apenas registrar. A assimetria é deliberada. Divergência de
        // contagem não bloqueia porque é um achado — o dinheiro já é o que é, e esconder a
        // diferença seria pior. Mesa aberta é o contrário: é uma porta que ainda dá para fechar
        // agora e não dará mais depois. ComandaService.addItem e cancelComanda exigem a sessão de
        // origem ABERTA, então uma comanda que sobreviva a este fechamento passa a responder 409
        // CASH_REGISTER_SESSION_CLOSED para sempre — a mesa congela, e o estoque já debitado item
        // a item fica sem nenhum caminho de devolução. Até 2026-08-28 a regra existia só no
        // cliente (frontend-admin-prod), e o servidor aceitava.
        List<Long> openComandaIds = comandaRepository.findOpenIdsBySessionId(sessionId);
        if (!openComandaIds.isEmpty()) {
            throw new CashRegisterSessionHasOpenComandasException(sessionId, openComandaIds);
        }
        //
        // PDV-F006: só DINHEIRO entra na conferência da gaveta. Débito, crédito e PIX não passam
        // pela mão do operador — eles se conferem contra o extrato da adquirente, não contra o
        // contado aqui. Somar tudo (como antes de order_payment existir) faria o fechamento
        // acusar sobra sempre que houvesse venda no cartão.
        //
        // PDV-C017/C018: e o esperado tem que contar o que SAIU, não só o que entrou. Até aqui a
        // fórmula somava entradas e nada mais, e as duas saídas em espécie ficavam de fora:
        //
        //   • o TROCO (PDV-C017). `order_payment.amount` em DINHEIRO é o valor ENTREGUE pelo
        //     cliente, não o retido — é assim que validatePaymentsAndComputeChange deriva o troco,
        //     e é o valor inteiro que o cliente estendeu que vira a linha de pagamento. A cédula
        //     do troco volta para a mão dele na mesma hora. Sem subtrair, toda venda em dinheiro
        //     com troco inflava o esperado exatamente pelo troco, e o operador honesto fechava o
        //     turno acusando uma FALTA que era só aritmética — todo dia, em toda venda quebrada.
        //   • o ESTORNO (PDV-C018). O ledger é append-only de propósito: refundOrder grava uma
        //     linha REFUNDED nova e deixa a CAPTURED original de pé, que é o desenho certo para o
        //     histórico. A consequência é que a soma dos capturados descreve tudo que entrou e
        //     nada do que voltou; a cédula devolvida ao cliente continuava contada na gaveta.
        BigDecimal expected = session.openingAmount()
                .add(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(sessionId, PaymentMethod.DINHEIRO))
                .subtract(orderPaymentRepository.sumRefundedAmountBySessionIdAndMethod(sessionId, PaymentMethod.DINHEIRO))
                .subtract(orderRepository.sumChangeAmountBySessionId(sessionId))
                .add(cashMovementRepository.sumSignedAmountBySessionId(sessionId))
                // CRM-F010 — quitação de marcado em dinheiro recebida neste caixa. receivable_payment
                // guarda o abatido, já líquido do troco devolvido.
                .add(receivableUseCase.receivedInSession(sessionId, PaymentMethod.DINHEIRO));

        // Divergência não bloqueia — é o achado do fechamento, como no balanço de inventário.
        return cashRegisterRepository.save(session.closedWith(expected, countedAmount, username, notes));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTotal> getSessionPaymentTotals(Long sessionId) {
        getSession(sessionId);
        List<PaymentTotal> totals = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            // GATEWAY_PIX (ECM-F004) nunca é lançado pelo operador nem amarrado a uma sessão de
            // caixa — é capturado pelo webhook do gateway, fora do ciclo de caixa. Incluí-lo aqui
            // só poluiria o fechamento com uma linha sempre zerada.
            if (method == PaymentMethod.GATEWAY_PIX) {
                continue;
            }
            // CRM-F010 — MARCADO é dinheiro que NÃO entrou: nunca aparece no caixa. O que entra é
            // a quitação, somada abaixo no método em que foi recebida.
            if (method == PaymentMethod.MARCADO) {
                continue;
            }
            // PDV-F026 — além do bruto, o estorno e (em dinheiro) o troco, para a aba Caixas mostrar o
            // líquido sem abrir recibo nenhum. Mesmas somas do esperado de closeSession.
            BigDecimal captured = orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(sessionId, method);
            BigDecimal refunded = orderPaymentRepository.sumRefundedAmountBySessionIdAndMethod(sessionId, method);
            // CRM-F010 — quitação de marcado recebida NESTA sessão (o caixa de quem recebe). Já é o
            // valor abatido, líquido do troco: o troco da quitação não entra de novo na conta.
            BigDecimal receivable = receivableUseCase.receivedInSession(sessionId, method);
            BigDecimal change = method == PaymentMethod.DINHEIRO
                    ? orderRepository.sumChangeAmountBySessionId(sessionId)
                    : BigDecimal.ZERO;
            totals.add(new PaymentTotal(method, captured, refunded, change,
                    captured.add(receivable).subtract(refunded).subtract(change), receivable));
        }
        return totals;
    }

    // ── Venda ────────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Order registerSale(Long sessionId, Long customerId, List<SaleItemCommand> items,
            List<PaymentCommand> payments, String username, boolean reserveForPickup, OrderDelivery delivery) {
        CashRegisterSession session = requireOwnOpenSession(sessionId, username);
        requireSessionFromToday(session);

        // PDV-F004: o preço e o custo vêm do catálogo. resolveSaleInfo já lança
        // ProductNotFoundException para SKU inexistente, e fromCatalog recusa produto sem preço —
        // as duas checagens acontecem ANTES de qualquer escrita de estoque.
        List<OrderItem> orderItems = new ArrayList<>(items.size());
        for (SaleItemCommand command : items) {
            EstoqueUseCase.CatalogSaleInfo saleInfo = estoqueUseCase.resolveSaleInfo(command.sku());
            OrderItem item = OrderItem.fromCatalog(command.sku(), command.quantity(),
                    saleInfo.pricing(), command.discountAmount(), saleInfo.productName())
                    .withNotes(command.note());
            // CRM-F003: a taxa é resolvida e carimbada aqui — mudar a taxa amanhã não pode
            // reescrever o cashback gerado por pedidos de ontem.
            CashbackRate resolvedRate = cashbackUseCase.resolveApplicableRate(command.sku());
            if (resolvedRate != null) {
                item = item.withCashbackPercent(resolvedRate.percent());
            }
            orderItems.add(item);
        }

        // PDV-C004: o depósito vem da SESSÃO, não do request. É o que impede o operador de baixar
        // estoque de um depósito que não é o do caixa dele.
        String warehouseCode = session.warehouseCode();
        Order order = Order.openBalcao(sessionId, warehouseCode, customerId, orderItems, delivery);
        requireDiscountWithinLimit(order);

        // PDV-F006: pagamento é validado ANTES de tocar o estoque — um pagamento insuficiente não
        // deveria custar um adjustStock que só vai ser desfeito pelo rollback da transação.
        // PDV-F022: contra totalPayable(), que inclui a taxa de entrega — no balcão sem entrega é
        // igual ao líquido, como sempre foi.
        BigDecimal changeAmount = validatePaymentsAndComputeChange(payments, order.totalPayable());
        // CRM-F010 — o marcar é validado junto com o pagamento, antes do estoque.
        validateOnAccount(customerId, payments);

        for (OrderItem item : order.items()) {
            estoqueUseCase.adjustStock(item.sku(), warehouseCode, MovementType.SAIDA, item.quantity(),
                    "Venda balcão sessão #" + sessionId, username);
        }

        // No balcão a mercadoria sai e o dinheiro entra no mesmo instante: CRIADO → CONCLUIDO (ou,
        // com reserva para retirada depois — PDV-F008 —, CRIADO → RESERVADO) na mesma transação. A
        // numeração é consumida aqui, e não na criação, nos dois casos.
        // PDV-F022: ENTREGA reserva — a mercadoria ainda não saiu da loja. RETIRADA só reserva com
        // reserveForPickup (cliente volta depois); a retirada imediata é o caso comum e conclui.
        boolean reserve = reserveForPickup
                || (delivery != null && delivery.type() == DeliveryType.ENTREGA);
        Order saved = orderRepository.save(reserve
                ? order.reserved(orderRepository.nextOrderNumber(), changeAmount, Instant.now())
                : order.concluded(orderRepository.nextOrderNumber(), changeAmount, Instant.now()));

        for (PaymentCommand payment : payments) {
            orderPaymentRepository.save(toPaymentLine(saved.id(), payment));
        }
        recordReceivableIfOnAccount(saved, null, payments, username);
        // Depois das linhas: o cashback desconta a fração marcada (ver CashbackService).
        cashbackUseCase.recordEarnedForOrder(saved);
        return saved;
    }

    // ── CRM-F010: "Marcar" ───────────────────────────────────────────────────────────────────

    /**
     * Linha de pagamento da venda: MARCADO nasce ON_ACCOUNT (fora do caixa); as demais, CAPTURED.
     * Package-private — {@code ComandaService} grava as linhas do fechamento de mesa pelo mesmo molde.
     */
    static OrderPayment toPaymentLine(Long orderId, PaymentCommand payment) {
        return payment.isOnAccount()
                ? OrderPayment.onAccount(orderId, payment.amount(), payment.dueDate())
                : OrderPayment.captured(orderId, payment.method(), payment.amount(), payment.installments(),
                        payment.channel(), payment.provider());
    }

    /** Package-private — a mesa valida o marcar pela mesma regra do balcão. */
    void validateOnAccount(Long customerId, List<PaymentCommand> payments) {
        receivableUseCase.validateOnAccount(customerId, payments);
    }

    /** Package-private — cria o recebível do pedido, se a venda teve linha MARCADO. */
    void recordReceivableIfOnAccount(Order saved, Long comandaId, List<PaymentCommand> payments, String username) {
        payments.stream().filter(PaymentCommand::isOnAccount).findFirst()
                .ifPresent(line -> receivableUseCase.createFromOrder(saved, comandaId, line, username));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPayment> getOrderPayments(Long orderId) {
        getOrder(orderId);
        return orderPaymentRepository.findByOrderId(orderId);
    }

    /**
     * Valida a lista de pagamentos contra o líquido do pedido e devolve o troco.
     *
     * <p>Regra: a soma do que <b>não</b> é {@code DINHEIRO} não pode passar do líquido — débito,
     * crédito e PIX são lançados pelo valor exato que o operador decide cobrar, e só dinheiro pode
     * ser tendido a mais. Isso garante que todo excedente é explicável por dinheiro, e o troco é
     * simplesmente {@code total pago − líquido}.</p>
     *
     * <p>Package-private — ver a nota em {@link #requireOwnOpenSession}: {@code ComandaService}
     * reaproveita esta mesma regra no fechamento da comanda, em vez de duplicar uma lógica que já
     * foi endurecida uma vez (a regra de troco em pagamento dividido é mais estrita que o desenho
     * original do plano).</p>
     */
    BigDecimal validatePaymentsAndComputeChange(List<PaymentCommand> payments, BigDecimal netAmount) {
        BigDecimal nonCashTotal = BigDecimal.ZERO;
        BigDecimal cashTotal = BigDecimal.ZERO;
        for (PaymentCommand payment : payments) {
            if (payment.method() == PaymentMethod.DINHEIRO) {
                cashTotal = cashTotal.add(payment.amount());
            } else {
                nonCashTotal = nonCashTotal.add(payment.amount());
            }
        }
        if (nonCashTotal.compareTo(netAmount) > 0) {
            throw new PaymentExceedsOrderTotalException(nonCashTotal, netAmount);
        }
        BigDecimal totalPaid = cashTotal.add(nonCashTotal);
        if (totalPaid.compareTo(netAmount) < 0) {
            throw new InsufficientPaymentException(totalPaid, netAmount);
        }
        BigDecimal change = totalPaid.subtract(netAmount);
        return change.signum() > 0 ? change : null;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listSessionOrders(Long sessionId, int page, int size) {
        getSession(sessionId);
        return orderRepository.findBySessionId(sessionId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listPendingOnlineOrders(int page, int size) {
        return orderRepository.findAll(SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO,
                null, null, null, page, size);
    }

    @Override
    @Transactional
    public Order settleOnlineOrder(Long sessionId, Long orderId, List<PaymentCommand> payments,
            String username) {
        CashRegisterSession session = requireOwnOpenSession(sessionId, username);
        Order order = getOrder(orderId);

        // PDV-C015 — o pagamento é validado ANTES de consumir a reserva, mesma ordem de
        // registerSale: um pagamento recusado não deveria custar uma reserva consumida que só o
        // rollback desfaz.
        //
        // Valor EXATO, sem troco: o canal continua MARKETPLACE e Order recusa changeAmount
        // positivo ali (ck_sales_order_change_amount_by_channel). Aceitar o excedente sem ter onde
        // gravá-lo faria a linha de pagamento afirmar que entrou na gaveta mais do que ficou — o
        // mesmo defeito que PDV-C017 acabou de tirar do fechamento, entrando de novo por outra
        // porta. Ver ChangeNotSupportedException.
        if (payments.stream().anyMatch(PaymentCommand::isOnAccount)) {
            throw new OnAccountNotSupportedException("liquidação de pedido do app");
        }
        BigDecimal change = validatePaymentsAndComputeChange(payments, order.netAmount());
        if (change != null && change.signum() > 0) {
            throw new ChangeNotSupportedException(order.netAmount().add(change), order.netAmount());
        }

        // A reserva já segurou a mercadoria desde o checkout: consumi-la converte o reservado em
        // saída real. Chamar adjustStock(SAIDA) aqui debitaria o estoque duas vezes.
        estoqueUseCase.consumeReservationsByOwner(Order.reservationOwnerReference(orderId), username);

        // O canal continua MARKETPLACE — quem muda é o sessionId, que passa a dizer qual caixa
        // recebeu o dinheiro. concluded() valida a transição e recusa pedido que não está
        // aguardando pagamento.
        Order saved = orderRepository.save(order
                .withSession(session.id())
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));

        // PDV-C015 — o que faltava: até aqui a liquidação era o ÚNICO caminho de recebimento do
        // projeto que não gravava linha de pagamento. O dinheiro entrava na gaveta e o ledger não
        // sabia: closeSession soma order_payment, então o esperado não contava esta cédula e o
        // fechamento acusava SOBRA sem dono; /payment-totals não via o valor; e o comprovante saía
        // com a lista de pagamentos vazia.
        for (PaymentCommand payment : payments) {
            orderPaymentRepository.save(OrderPayment.captured(saved.id(), payment.method(),
                    payment.amount(), payment.installments(), payment.channel(), payment.provider()));
        }
        // E a cobrança de gateway aberta no checkout (ShopService grava uma PENDING/GATEWAY_PIX em
        // todo pedido de marketplace) é encerrada: pago no balcão, nenhum webhook vai confirmá-la,
        // e deixá-la PENDING para sempre descreveria uma cobrança em aberto que não existe.
        cancelPendingGatewayCharges(saved.id());

        cashbackUseCase.recordEarnedForOrder(saved);
        return saved;
    }

    /**
     * Encerra as cobranças ainda {@code PENDING} do pedido (PDV-C015).
     *
     * <p>Atualiza a própria linha em vez de acrescentar uma — ver o javadoc de
     * {@link OrderPayment#cancelled()}: uma linha nova ao lado deixaria a {@code PENDING} de pé,
     * que é o que este passo existe para não deixar. Só {@code PENDING} é tocada; captura e
     * estorno são eventos de dinheiro e continuam intocáveis.</p>
     */
    private void cancelPendingGatewayCharges(Long orderId) {
        for (OrderPayment payment : orderPaymentRepository.findByOrderId(orderId)) {
            if (payment.status() == PaymentStatus.PENDING) {
                orderPaymentRepository.save(payment.cancelled());
            }
        }
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    /**
     * Sessão aberta <b>e</b> do próprio operador. É a checagem que fecha o buraco de isolamento
     * documentado no README do módulo: antes, qualquer um com {@code PDV_SALE_MANAGE} vendia na
     * sessão de outro, e o fechamento daquele caixa acusava uma diferença sem dono.
     *
     * <p>Package-private (não {@code private}) de propósito: {@code ComandaService} (PDV-F009,
     * mesmo pacote) reaproveita esta checagem via injeção do bean concreto {@code PdvService}, em
     * vez de duplicar a regra de posse de sessão.</p>
     */
    /**
     * PDV-F022 — venda só no caixa de HOJE, na data da loja. Barra o caixa de ontem esquecido
     * aberto, que misturaria dois dias num fechamento. Só a venda de balcão passa por aqui:
     * fechar o caixa antigo continua permitido, e a mesa que vira a madrugada não é afetada.
     */
    private void requireSessionFromToday(CashRegisterSession session) {
        LocalDate openedOn = LocalDate.ofInstant(session.openedAt(), ZONA_LOJA);
        LocalDate today = LocalDate.now(clock.withZone(ZONA_LOJA));
        if (openedOn.isBefore(today)) {
            throw new CashRegisterSessionStaleException(session.id(), openedOn, today);
        }
    }

    CashRegisterSession requireOwnOpenSession(Long sessionId, String username) {
        CashRegisterSession session = getSession(sessionId);
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        if (!session.belongsTo(username)) {
            throw new CashRegisterSessionNotOwnedException(sessionId, username);
        }
        return session;
    }

    /**
     * Sessão aberta, <b>sem</b> exigir posse (PDV-F010).
     *
     * <p>Existe para a comanda de mesa, e só para ela. A decisão do dono é <b>caixa por atendente,
     * mesas compartilhadas</b>: quem assume o posto do colega precisa lançar, fechar e cancelar as
     * mesas do salão, mantendo cada um a sua gaveta.</p>
     *
     * <p><b>Não é regressão do isolamento de PDV-C004.</b> Aquele resolveu o buraco da <i>venda de
     * balcão</i>, onde vender no caixa alheio criava diferença sem dono — e continua valendo:
     * {@link #registerSale}, {@link #registerCashMovement} e a abertura de sessão seguem em
     * {@link #requireOwnOpenSession}. Mesa é outro caso: o consumo é do salão, não do operador. O
     * controle de acesso da mesa é a permissão {@code PDV_COMANDA_MANAGE}, não a posse da gaveta.
     * Precedente do mesmo espírito já aceito no projeto: a lista de reposição, por armazém e
     * compartilhada entre operadores.</p>
     */
    CashRegisterSession requireOpenSession(Long sessionId) {
        CashRegisterSession session = getSession(sessionId);
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        return session;
    }

    /** Package-private — ver a nota em {@link #requireOwnOpenSession}. */
    void requireDiscountWithinLimit(Order order) {
        requireDiscountWithinLimit(order, BigDecimal.ZERO);
    }

    /**
     * PDV-F019 — mesmo teto, descontando do total o que é desconto de kit montável. O desconto do
     * kit é preço de catálogo configurado pelo admin, não abatimento concedido pelo atendente, e é
     * este segundo que o teto existe para limitar: sem a isenção, um kit com 10% bloquearia
     * qualquer desconto de conta numa mesa com teto de 10%.
     */
    void requireDiscountWithinLimit(Order order, BigDecimal exemptDiscount) {
        BigDecimal granted = order.discountAmount().subtract(exemptDiscount == null ? BigDecimal.ZERO : exemptDiscount);
        if (granted.signum() <= 0 || order.grossAmount().signum() == 0) {
            return;
        }
        BigDecimal percent = granted
                .divide(order.grossAmount(), Money.INTERMEDIATE_SCALE, Money.ROUNDING)
                .multiply(Money.HUNDRED)
                .setScale(Money.PERCENT_SCALE, Money.ROUNDING);
        if (percent.compareTo(maxDiscountPercent) > 0) {
            throw new DiscountLimitExceededException(percent, maxDiscountPercent);
        }
    }
}
