package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.recebivel.CreditLimitExceededException;
import com.cernecommerce.core.domain.exception.recebivel.CustomerHasOverdueException;
import com.cernecommerce.core.domain.exception.recebivel.CustomerNotEligibleForOnAccountException;
import com.cernecommerce.core.domain.exception.recebivel.CustomerRequiredForOnAccountException;
import com.cernecommerce.core.domain.exception.recebivel.DuplicateOnAccountPaymentException;
import com.cernecommerce.core.domain.exception.recebivel.InvalidDueDateException;
import com.cernecommerce.core.domain.exception.recebivel.OnAccountNotSupportedException;
import com.cernecommerce.core.domain.exception.recebivel.PaymentExceedsBalanceException;
import com.cernecommerce.core.domain.exception.recebivel.ReceivableNotFoundException;
import com.cernecommerce.core.domain.exception.recebivel.ReceivableNotOpenException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.OnAccountEligibility;
import com.cernecommerce.core.domain.model.recebivel.ReceivableCustomerSummary;
import com.cernecommerce.core.domain.model.recebivel.ReceivableFilter;
import com.cernecommerce.core.domain.model.recebivel.ReceivableItem;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePayment;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePaymentBatch;
import com.cernecommerce.core.domain.model.recebivel.ReceivableStatus;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerCreditLimitRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerReceivableRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Marcar" — venda a prazo para cliente VIP (CRM-F010). Ver {@link ReceivableUseCase}.
 *
 * <p><b>Vencido é calculado na leitura</b> ({@code dueDate < hoje} sobre os em aberto), além de
 * gravado pelo job diário: a regra "quem tem vencido não marca" vale desde a meia-noite, sem depender
 * de o job já ter rodado.</p>
 */
public class ReceivableService implements ReceivableUseCase {

    static final String VIP_TAG = "VIP";
    static final String DEFAULT_DUE_DAYS_KEY = "pdv.on-account.default-due-days";
    static final String DEFAULT_CREDIT_LIMIT_KEY = "pdv.on-account.default-credit-limit";
    private static final ZoneId ZONA_LOJA = ZoneId.of("America/Sao_Paulo");

    private final CustomerReceivableRepository receivableRepository;
    private final CustomerCreditLimitRepository creditLimitRepository;
    private final CustomerRepository customerRepository;
    private final CustomerTagRepository customerTagRepository;
    private final OrderRepository orderRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final CashbackUseCase cashbackUseCase;
    private final SystemConfigPort systemConfigPort;
    private final Clock clock;

    public ReceivableService(CustomerReceivableRepository receivableRepository,
            CustomerCreditLimitRepository creditLimitRepository, CustomerRepository customerRepository,
            CustomerTagRepository customerTagRepository, OrderRepository orderRepository,
            CashRegisterRepository cashRegisterRepository, CashbackUseCase cashbackUseCase,
            SystemConfigPort systemConfigPort, Clock clock) {
        this.receivableRepository = receivableRepository;
        this.creditLimitRepository = creditLimitRepository;
        this.customerRepository = customerRepository;
        this.customerTagRepository = customerTagRepository;
        this.orderRepository = orderRepository;
        this.cashRegisterRepository = cashRegisterRepository;
        this.cashbackUseCase = cashbackUseCase;
        this.systemConfigPort = systemConfigPort;
        this.clock = clock;
    }

    // ── Na venda ─────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public void validateOnAccount(Long customerId, List<PaymentCommand> payments) {
        List<PaymentCommand> marked = payments.stream().filter(PaymentCommand::isOnAccount).toList();
        if (marked.isEmpty()) {
            return;
        }
        if (marked.size() > 1) {
            throw new DuplicateOnAccountPaymentException();
        }
        PaymentCommand line = marked.get(0);
        LocalDate today = today();
        if (line.dueDate() == null || line.dueDate().isBefore(today)) {
            throw new InvalidDueDateException(line.dueDate(), today);
        }
        if (customerId == null) {
            throw new CustomerRequiredForOnAccountException();
        }
        requireCustomer(customerId);
        if (!isVip(customerId)) {
            throw new CustomerNotEligibleForOnAccountException(customerId);
        }
        BigDecimal overdue = receivableRepository.sumOverdueBalance(customerId, today);
        if (overdue.signum() > 0) {
            throw new CustomerHasOverdueException(customerId, overdue);
        }
        BigDecimal limit = effectiveCreditLimit(customerId);
        BigDecimal open = receivableRepository.sumOpenBalance(customerId);
        if (open.add(line.amount()).compareTo(limit) > 0) {
            throw new CreditLimitExceededException(limit, open, available(limit, open));
        }
    }

    @Override
    @Transactional
    public CustomerReceivable createFromOrder(Order order, Long comandaId, PaymentCommand onAccountLine,
            String username) {
        // O pedido inteiro, mesmo quando só parte foi marcada: "R$ 30 de R$ 80 marcados".
        List<ReceivableItem> items = order.items().stream().map(ReceivableService::snapshot).toList();
        return receivableRepository.save(CustomerReceivable.open(order.customerId(), order.id(), comandaId,
                order.sessionId(), onAccountLine.amount(), onAccountLine.dueDate(), username, Instant.now(), items));
    }

    private static ReceivableItem snapshot(OrderItem item) {
        String mode = item.mode().isCatalogLine() ? null : item.mode().name();
        return new ReceivableItem(null, item.id(), item.sku(), item.productName(), item.quantity(),
                item.netAmount(), mode);
    }

    // ── Consulta ─────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public OnAccountEligibility eligibility(Long customerId, boolean operatorMayMark) {
        requireCustomer(customerId);
        LocalDate today = today();
        BigDecimal limit = effectiveCreditLimit(customerId);
        BigDecimal open = receivableRepository.sumOpenBalance(customerId);
        BigDecimal overdue = receivableRepository.sumOverdueBalance(customerId, today);
        BigDecimal available = available(limit, open);

        List<String> reasons = new ArrayList<>();
        if (!isVip(customerId)) {
            reasons.add("CUSTOMER_NOT_ELIGIBLE");
        }
        if (!operatorMayMark) {
            reasons.add("ON_ACCOUNT_NOT_ALLOWED");
        }
        if (overdue.signum() > 0) {
            reasons.add("CUSTOMER_HAS_OVERDUE");
        }
        if (available.signum() <= 0) {
            reasons.add("CREDIT_LIMIT_EXCEEDED");
        }
        LocalDate defaultDueDate = today.plusDays(systemConfigPort.getInt(DEFAULT_DUE_DAYS_KEY, 30));
        return new OnAccountEligibility(reasons.isEmpty(), List.copyOf(reasons), limit, open, overdue, available,
                defaultDueDate);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ReceivableView> list(ReceivableFilter filter, int page, int size) {
        PageResult<CustomerReceivable> result = receivableRepository.findAll(filter, today(), page, size);
        return new PageResult<>(toViews(result.content()), result.page(), result.size(), result.totalElements(),
                result.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceivableCustomerSummary> summary(ReceivableStatus status, Boolean overdue) {
        LocalDate today = today();
        var statuses = status != null ? EnumSet.of(status)
                : EnumSet.of(ReceivableStatus.ABERTO, ReceivableStatus.PARCIAL, ReceivableStatus.VENCIDO);
        List<CustomerReceivableRepository.CustomerBalanceRow> rows =
                receivableRepository.summarizeByCustomer(statuses, today).stream()
                        .filter(r -> !Boolean.TRUE.equals(overdue) || r.overdueBalance().signum() > 0)
                        .toList();
        List<Long> customerIds = rows.stream().map(CustomerReceivableRepository.CustomerBalanceRow::customerId).toList();
        Map<Long, String> names = customerNames(customerIds);
        Map<Long, BigDecimal> limits = creditLimitRepository.findByCustomerIds(customerIds);
        BigDecimal defaultLimit = defaultCreditLimit();
        return rows.stream()
                .map(r -> new ReceivableCustomerSummary(r.customerId(), names.get(r.customerId()), r.openBalance(),
                        r.overdueBalance(), limits.getOrDefault(r.customerId(), defaultLimit), r.nextDueDate(),
                        r.count()))
                .sorted(Comparator.comparing(ReceivableCustomerSummary::overdueBalance).reversed()
                        .thenComparing(ReceivableCustomerSummary::openBalance, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReceivableView get(Long id) {
        return toViews(List.of(requireReceivable(id))).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceivableView> listByCustomer(Long customerId) {
        requireCustomer(customerId);
        return toViews(receivableRepository.findByCustomerId(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerReceivable> findByOrderId(Long orderId) {
        return receivableRepository.findByOrderId(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerBalance balance(Long customerId) {
        return new CustomerBalance(effectiveCreditLimit(customerId), receivableRepository.sumOpenBalance(customerId),
                receivableRepository.sumOverdueBalance(customerId, today()));
    }

    // ── Quitação ─────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SettlementResult pay(Long sessionId, String username, Long customerId, List<PaymentCommand> payments,
            List<Long> receivableIds) {
        // A mesma regra da venda de balcão: o dinheiro entra na gaveta de quem recebe, hoje.
        CashRegisterSession session = requireOwnOpenSession(sessionId, username);
        requireCustomer(customerId);
        for (PaymentCommand payment : payments) {
            if (payment.method() == PaymentMethod.MARCADO || payment.method() == PaymentMethod.GATEWAY_PIX) {
                throw new OnAccountNotSupportedException("quitação de marcado (" + payment.method() + ")");
            }
        }

        List<CustomerReceivable> targets = resolveTargets(customerId, receivableIds);
        BigDecimal balance = targets.stream().map(CustomerReceivable::amountOpen)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nonCash = payments.stream().filter(p -> p.method() != PaymentMethod.DINHEIRO)
                .map(PaymentCommand::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cash = payments.stream().filter(p -> p.method() == PaymentMethod.DINHEIRO)
                .map(PaymentCommand::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (nonCash.compareTo(balance) > 0) {
            throw new PaymentExceedsBalanceException(nonCash, balance);
        }
        BigDecimal received = nonCash.add(cash);
        BigDecimal applied = received.min(balance);
        BigDecimal change = received.subtract(applied);

        Instant now = Instant.now();
        LocalDate today = today();
        ReceivablePaymentBatch batch = receivableRepository.saveBatch(new ReceivablePaymentBatch(null, customerId,
                session.id(), change, username, now));

        // Abate as linhas não-dinheiro primeiro: o que sobrar de dinheiro é o troco.
        List<PaymentCommand> ordered = new ArrayList<>(payments);
        ordered.sort(Comparator.comparing(p -> p.method() == PaymentMethod.DINHEIRO));
        Map<Long, BigDecimal> appliedById = new LinkedHashMap<>();
        int t = 0;
        BigDecimal targetLeft = targets.isEmpty() ? BigDecimal.ZERO : targets.get(0).amountOpen();
        for (PaymentCommand payment : ordered) {
            BigDecimal lineLeft = payment.amount();
            while (lineLeft.signum() > 0 && t < targets.size()) {
                CustomerReceivable target = targets.get(t);
                BigDecimal portion = lineLeft.min(targetLeft);
                receivableRepository.savePayment(new ReceivablePayment(null, target.id(), batch.id(), customerId,
                        portion, payment.method(), payment.installments(), payment.channel(), payment.provider(),
                        session.id(), username, now));
                appliedById.merge(target.id(), portion, BigDecimal::add);
                lineLeft = lineLeft.subtract(portion);
                targetLeft = targetLeft.subtract(portion);
                if (targetLeft.signum() == 0) {
                    t++;
                    targetLeft = t < targets.size() ? targets.get(t).amountOpen() : BigDecimal.ZERO;
                }
            }
        }

        Map<Long, CustomerReceivable> byId = targets.stream()
                .collect(Collectors.toMap(CustomerReceivable::id, Function.identity()));
        List<AppliedPayment> result = new ArrayList<>(appliedById.size());
        for (Map.Entry<Long, BigDecimal> entry : appliedById.entrySet()) {
            CustomerReceivable target = byId.get(entry.getKey());
            CustomerReceivable saved = receivableRepository.save(target.applyPayment(entry.getValue(), now, today));
            // Cashback da parte marcada: creditado agora, proporcional ao que foi quitado.
            orderRepository.findById(target.orderId()).ifPresent(order ->
                    cashbackUseCase.recordEarnedForReceivablePayment(order, entry.getValue()));
            result.add(new AppliedPayment(saved.id(), entry.getValue(), saved.status()));
        }
        return new SettlementResult(batch.id(), result, change, receivableRepository.sumOpenBalance(customerId));
    }

    private List<CustomerReceivable> resolveTargets(Long customerId, List<Long> receivableIds) {
        List<CustomerReceivable> open = receivableRepository.findOpenByCustomerIdForUpdate(customerId);
        if (receivableIds == null || receivableIds.isEmpty()) {
            if (open.isEmpty()) {
                throw new ReceivableNotOpenException("O cliente " + customerId + " não tem marcado em aberto");
            }
            return open;
        }
        Map<Long, CustomerReceivable> openById = open.stream()
                .collect(Collectors.toMap(CustomerReceivable::id, Function.identity()));
        List<CustomerReceivable> chosen = new ArrayList<>(receivableIds.size());
        for (Long id : receivableIds.stream().distinct().toList()) {
            CustomerReceivable receivable = openById.get(id);
            if (receivable == null) {
                CustomerReceivable any = requireReceivable(id);
                if (!any.customerId().equals(customerId)) {
                    throw new ReceivableNotOpenException("O marcado " + id + " não é do cliente " + customerId);
                }
                throw new ReceivableNotOpenException(id, any.status());
            }
            chosen.add(receivable);
        }
        return chosen;
    }

    // ── Manutenção ───────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CustomerReceivable changeDueDate(Long id, LocalDate dueDate, String username) {
        LocalDate today = today();
        if (dueDate == null || dueDate.isBefore(today)) {
            throw new InvalidDueDateException(dueDate, today);
        }
        CustomerReceivable receivable = requireReceivable(id);
        if (!receivable.status().isOpen()) {
            throw new ReceivableNotOpenException(id, receivable.status());
        }
        return receivableRepository.save(receivable.withDueDate(dueDate));
    }

    @Override
    @Transactional
    public CustomerReceivable cancel(Long id, String reason, String username) {
        CustomerReceivable receivable = requireReceivable(id);
        if (!receivable.status().isOpen()) {
            throw new ReceivableNotOpenException(id, receivable.status());
        }
        return receivableRepository.save(receivable.cancelled(reason, username, Instant.now()));
    }

    @Override
    @Transactional
    public void cancelOpenForOrder(Long orderId, String reason, String username) {
        receivableRepository.findByOrderId(orderId)
                .filter(r -> r.status().isOpen())
                .ifPresent(r -> receivableRepository.save(r.cancelled(
                        reason == null || reason.isBlank() ? "Pedido de origem reembolsado" : reason,
                        username, Instant.now())));
    }

    @Override
    @Transactional
    public int markOverdue() {
        return receivableRepository.markOverdue(today());
    }

    @Override
    @Transactional
    public CreditLimitChange setCreditLimit(Long customerId, BigDecimal creditLimit, String username) {
        requireCustomer(customerId);
        if (creditLimit != null && creditLimit.signum() < 0) {
            throw new IllegalArgumentException("creditLimit não pode ser negativo");
        }
        BigDecimal before = effectiveCreditLimit(customerId);
        if (creditLimit == null) {
            creditLimitRepository.delete(customerId);
        } else {
            creditLimitRepository.save(customerId, creditLimit, username, Instant.now());
        }
        return new CreditLimitChange(before, effectiveCreditLimit(customerId));
    }

    // ── Caixa ────────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public BigDecimal receivedInSession(Long sessionId, PaymentMethod method) {
        return receivableRepository.sumReceivedBySessionAndMethod(sessionId, method);
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONA_LOJA));
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private CustomerReceivable requireReceivable(Long id) {
        return receivableRepository.findById(id).orElseThrow(() -> new ReceivableNotFoundException(id));
    }

    /** Enquanto não houver o campo oficial {@code vip}, VIP é a tag do CRM. */
    private boolean isVip(Long customerId) {
        return customerTagRepository.findTagsByCustomerId(customerId).stream()
                .anyMatch(tag -> tag.nome() != null && VIP_TAG.equalsIgnoreCase(tag.nome().trim()));
    }

    private BigDecimal defaultCreditLimit() {
        return systemConfigPort.getDecimal(DEFAULT_CREDIT_LIMIT_KEY, BigDecimal.ZERO);
    }

    private BigDecimal effectiveCreditLimit(Long customerId) {
        return creditLimitRepository.findByCustomerId(customerId).orElseGet(this::defaultCreditLimit);
    }

    private static BigDecimal available(BigDecimal limit, BigDecimal open) {
        return limit.subtract(open).max(BigDecimal.ZERO);
    }

    /** Mesma regra de {@code PdvService.registerSale}: caixa aberto e do operador (o corte de meia-noite caiu no PDV-F037). */
    private CashRegisterSession requireOwnOpenSession(Long sessionId, String username) {
        CashRegisterSession session = cashRegisterRepository.findById(sessionId)
                .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        if (!session.belongsTo(username)) {
            throw new CashRegisterSessionNotOwnedException(sessionId, username);
        }
        return session;
    }

    private List<ReceivableView> toViews(List<CustomerReceivable> receivables) {
        if (receivables.isEmpty()) {
            return List.of();
        }
        LocalDate today = today();
        Map<Long, String> names = customerNames(receivables.stream().map(CustomerReceivable::customerId)
                .distinct().toList());
        Map<Long, Order> orders = new HashMap<>();
        for (Long orderId : receivables.stream().map(CustomerReceivable::orderId).distinct().toList()) {
            orderRepository.findById(orderId).ifPresent(o -> orders.put(orderId, o));
        }
        Map<Long, List<ReceivablePayment>> payments = receivableRepository.findPaymentsByReceivableIds(
                        receivables.stream().map(CustomerReceivable::id).toList()).stream()
                .collect(Collectors.groupingBy(ReceivablePayment::receivableId));
        return receivables.stream().map(r -> {
            Order order = orders.get(r.orderId());
            long daysOverdue = r.isOverdue(today) ? ChronoUnit.DAYS.between(r.dueDate(), today) : 0;
            return new ReceivableView(r, names.get(r.customerId()), order == null ? null : order.orderNumber(),
                    order == null ? null : order.tableLabel(), daysOverdue, payments.getOrDefault(r.id(), List.of()));
        }).toList();
    }

    private Map<Long, String> customerNames(List<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findByIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::id, Customer::nome, (a, b) -> a));
    }
}
