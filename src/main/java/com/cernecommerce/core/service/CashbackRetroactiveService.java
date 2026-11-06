package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.cashback.CashbackRetroactiveNotAllowedException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRetroactiveStaleException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackRetroactiveCalculation;
import com.cernecommerce.core.domain.model.cashback.CashbackRetroactiveCalculation.ItemCredit;
import com.cernecommerce.core.domain.model.cashback.CashbackRetroactivePreview;
import com.cernecommerce.core.domain.model.cashback.CashbackRetroactivePreview.OrderCredit;
import com.cernecommerce.core.domain.model.cashback.CashbackSettings;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CashbackRetroactiveUseCase;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.cashback.CashbackRateRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderItemRefundRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CashbackRetroactiveService implements CashbackRetroactiveUseCase {

    private static final int PAGE_SIZE = 200;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** Os mesmos status de {@code totalRevenueNet}: pagos, sem cancelado nem reembolsado por inteiro. */
    private static final Set<OrderStatus> PAID = Set.of(OrderStatus.PAGO, OrderStatus.SEPARADO, OrderStatus.ENVIADO,
            OrderStatus.ENTREGUE, OrderStatus.CONCLUIDO, OrderStatus.RESERVADO);
    private static final String NO_CPF = "Cliente sem CPF não participa do cashback.";
    private static final String NO_GLOBAL_RATE = "Não há taxa global de cashback ativa.";

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderItemRefundRepository orderItemRefundRepository;
    private final CashbackEntryRepository cashbackEntryRepository;
    private final CashbackRateRepository cashbackRateRepository;
    private final CashbackUseCase cashbackUseCase;

    public CashbackRetroactiveService(CustomerRepository customerRepository, OrderRepository orderRepository,
            OrderPaymentRepository orderPaymentRepository, OrderItemRefundRepository orderItemRefundRepository,
            CashbackEntryRepository cashbackEntryRepository, CashbackRateRepository cashbackRateRepository,
            CashbackUseCase cashbackUseCase) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.orderPaymentRepository = orderPaymentRepository;
        this.orderItemRefundRepository = orderItemRefundRepository;
        this.cashbackEntryRepository = cashbackEntryRepository;
        this.cashbackRateRepository = cashbackRateRepository;
        this.cashbackUseCase = cashbackUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public CashbackRetroactivePreview preview(Long customerId) {
        Customer customer = requireCustomer(customerId);
        if (!customer.isOfficiallyRegistered()) {
            return CashbackRetroactivePreview.blocked(NO_CPF);
        }
        Instant now = Instant.now();
        Optional<CashbackRate> rate = cashbackRateRepository.findGlobal(now);
        if (rate.isEmpty()) {
            return CashbackRetroactivePreview.blocked(NO_GLOBAL_RATE);
        }
        CashbackRetroactiveCalculation calculation = calculate(customerId, rate.get().percent());
        List<OrderCredit> orders = calculation.orders().stream()
                .map(order -> toOrderCredit(order, calculation.items()))
                .filter(credit -> credit.creditAmount().signum() > 0)
                .sorted(Comparator.comparing(OrderCredit::paidAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        CashbackSettings settings = cashbackUseCase.getSettings();
        return new CashbackRetroactivePreview(true, null, rate.get().percent(), orders.size(),
                sum(calculation.items(), ItemCredit::base), sum(calculation.items(), ItemCredit::credit),
                calculation.skippedAlreadyEarned(), now, expiresAt(now, settings), orders);
    }

    @Override
    @Transactional
    public Result grant(Long customerId, BigDecimal expectedCreditAmount) {
        Customer customer = requireCustomer(customerId);
        if (!customer.isOfficiallyRegistered()) {
            throw new CashbackRetroactiveNotAllowedException(CashbackRetroactiveNotAllowedException.CUSTOMER_NOT_ELIGIBLE,
                    NO_CPF);
        }
        Instant now = Instant.now();
        CashbackRate rate = cashbackRateRepository.findGlobal(now).orElseThrow(
                () -> new CashbackRetroactiveNotAllowedException(
                        CashbackRetroactiveNotAllowedException.GLOBAL_RATE_MISSING, NO_GLOBAL_RATE));
        // Dois admins ao mesmo tempo: o segundo espera aqui e recalcula já vendo os ganhos do primeiro.
        cashbackEntryRepository.lockCustomer(customerId);

        List<ItemCredit> items = calculate(customerId, rate.percent()).items();
        if (items.isEmpty()) {
            throw new CashbackRetroactiveNotAllowedException(CashbackRetroactiveNotAllowedException.NOTHING_TO_CREDIT,
                    "Nenhum pedido elegível para o crédito.");
        }
        BigDecimal total = sum(items, ItemCredit::credit);
        if (total.compareTo(expectedCreditAmount) != 0) {
            throw new CashbackRetroactiveStaleException(expectedCreditAmount, total);
        }
        Instant expiresAt = expiresAt(now, cashbackUseCase.getSettings());
        // Um lançamento por item, com o item preenchido: é o que deixa o índice único barrar duplicata e o
        // estorno parcial reverter o crédito. A data é a de agora: com a do pedido, quase tudo nasceria vencido.
        for (ItemCredit item : items) {
            cashbackEntryRepository.save(CashbackEntry.earned(customerId, item.orderId(), item.orderItemId(),
                    item.credit(), now, now, expiresAt));
        }
        int ordersCredited = (int) items.stream().map(ItemCredit::orderId).distinct().count();
        return new Result(ordersCredited, total, cashbackUseCase.getCustomerBalance(customerId));
    }

    private CashbackRetroactiveCalculation calculate(Long customerId, BigDecimal percent) {
        List<Order> eligible = new ArrayList<>();
        List<ItemCredit> items = new ArrayList<>();
        int skipped = 0;
        for (Order order : paidOrders(customerId)) {
            // Conta também ganho revertido: um crédito revogado não volta por aqui.
            if (cashbackEntryRepository.existsEarnedForOrder(order.id())) {
                skipped++;
                continue;
            }
            eligible.add(order);
            items.addAll(creditsOf(order, percent));
        }
        return new CashbackRetroactiveCalculation(eligible, items, skipped);
    }

    private List<Order> paidOrders(Long customerId) {
        List<Order> orders = new ArrayList<>();
        int page = 0;
        PageResult<Order> result;
        do {
            result = orderRepository.findAll(null, null, customerId, null, null, page++, PAGE_SIZE);
            result.content().stream().filter(order -> PAID.contains(order.status())).forEach(orders::add);
        } while (page < result.totalPages());
        return orders;
    }

    private List<ItemCredit> creditsOf(Order order, BigDecimal percent) {
        Map<Long, BigDecimal> refunded = orderItemRefundRepository.sumQuantityByOrderItem(order.id());
        BigDecimal onAccount = orderPaymentRepository.findByOrderId(order.id()).stream()
                .filter(payment -> payment.status() == PaymentStatus.ON_ACCOUNT)
                .map(OrderPayment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fraction = order.unmarkedFraction(onAccount).multiply(order.cashbackEarningFraction());

        List<ItemCredit> credits = new ArrayList<>();
        for (OrderItem item : order.items()) {
            BigDecimal net = item.netAmount();
            if (net == null || item.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal kept = item.quantity().subtract(refunded.getOrDefault(item.id(), BigDecimal.ZERO));
            if (kept.signum() <= 0) {
                continue;
            }
            BigDecimal base = net.multiply(kept).multiply(fraction)
                    .divide(item.quantity(), Money.MONEY_SCALE, Money.ROUNDING);
            BigDecimal credit = base.multiply(percent).divide(HUNDRED, Money.MONEY_SCALE, Money.ROUNDING);
            // Lançamento de valor zero não existe no extrato (ck_cashback_entry_amount_not_zero).
            if (credit.signum() > 0) {
                credits.add(new ItemCredit(order.id(), item.id(), base, credit));
            }
        }
        return credits;
    }

    private static OrderCredit toOrderCredit(Order order, List<ItemCredit> items) {
        List<ItemCredit> ofOrder = items.stream().filter(item -> item.orderId().equals(order.id())).toList();
        return new OrderCredit(order.id(), order.orderNumber(), order.paidAt(), sum(ofOrder, ItemCredit::base),
                sum(ofOrder, ItemCredit::credit));
    }

    private static BigDecimal sum(List<ItemCredit> items, java.util.function.Function<ItemCredit, BigDecimal> field) {
        return items.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Instant expiresAt(Instant now, CashbackSettings settings) {
        return now.plus(settings.expiracaoDias(), ChronoUnit.DAYS);
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
