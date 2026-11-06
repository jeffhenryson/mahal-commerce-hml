package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.exception.pedido.SalesGoalNotFoundException;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics.CustomerSort;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics.PaidOrder;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.pedido.SalesGoal;
import com.cernecommerce.core.ports.in.SalesAnalyticsUseCase;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.pedido.SalesAnalyticsRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Análise de Vendas (pendência 9). Agrupamentos por dia, hora, operador e cliente são feitos aqui, sobre
 * os pedidos pagos do período, no fuso da loja: SQL de data/hora com fuso é diferente em cada banco, e
 * o período tem teto de 366 dias, então o volume cabe em memória.
 */
public class SalesAnalyticsService implements SalesAnalyticsUseCase {

    static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_SLOW_MOVERS = 100;

    private final SalesAnalyticsRepository repository;
    private final CustomerRepository customerRepository;
    private final Clock clock;

    public SalesAnalyticsService(SalesAnalyticsRepository repository, CustomerRepository customerRepository) {
        this(repository, customerRepository, Clock.systemUTC());
    }

    SalesAnalyticsService(SalesAnalyticsRepository repository, CustomerRepository customerRepository, Clock clock) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesAnalytics.HeatmapCell> heatmap(SalesChannel channel, String warehouseCode, Instant from,
            Instant to) {
        checkPeriod(from, to);
        Map<Integer, BigDecimal[]> cells = new HashMap<>();
        for (PaidOrder o : repository.paidOrders(name(channel), blank(warehouseCode), from, to)) {
            ZonedDateTime local = o.soldAt().atZone(STORE_ZONE);
            int key = local.getDayOfWeek().getValue() * 100 + local.getHour();
            BigDecimal[] acc = cells.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(o.netAmount());
            acc[1] = acc[1].add(BigDecimal.ONE);
        }
        return cells.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new SalesAnalytics.HeatmapCell(e.getKey() / 100, e.getKey() % 100, e.getValue()[0],
                        e.getValue()[1].longValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesAnalytics.PaymentMethodTotal> paymentMethods(SalesChannel channel, String warehouseCode,
            Instant from, Instant to) {
        checkPeriod(from, to);
        return repository.paymentTotals(name(channel), blank(warehouseCode), from, to).stream()
                .sorted(Comparator.comparing(SalesAnalytics.PaymentMethodTotal::amount).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesAnalytics.OperatorTotal> operators(SalesChannel channel, String warehouseCode, Instant from,
            Instant to) {
        checkPeriod(from, to);
        List<PaidOrder> orders = repository.paidOrders(name(channel), blank(warehouseCode), from, to);
        Map<Long, String> sessionOperator = repository.sessionOperators(orders.stream().map(PaidOrder::sessionId)
                .filter(Objects::nonNull).distinct().toList());
        // Chave = username do operador; null = pedido sem caixa (pago online).
        Map<String, BigDecimal[]> byOperator = new LinkedHashMap<>();
        for (PaidOrder o : orders) {
            String operator = o.sessionId() == null ? null : sessionOperator.get(o.sessionId());
            BigDecimal[] acc = byOperator.computeIfAbsent(operator,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(o.netAmount());
            acc[1] = acc[1].add(BigDecimal.ONE);
        }
        Map<String, Long> userIds = repository.operatorUserIds(byOperator.keySet().stream()
                .filter(Objects::nonNull).toList());
        return byOperator.entrySet().stream()
                .map(e -> new SalesAnalytics.OperatorTotal(e.getKey() == null ? null : userIds.get(e.getKey()),
                        e.getKey(), e.getValue()[0], e.getValue()[1].longValue(),
                        average(e.getValue()[0], e.getValue()[1].longValue())))
                .sorted(Comparator.comparing(SalesAnalytics.OperatorTotal::revenue).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SalesAnalytics.CustomerRanking customers(SalesChannel channel, Instant from, Instant to, int page,
            int size, CustomerSort sort) {
        checkPeriod(from, to);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidReportPeriodException("page >= 0 e size entre 1 e " + MAX_PAGE_SIZE);
        }
        List<PaidOrder> orders = repository.paidOrders(name(channel), null, from, to);
        Map<Long, List<PaidOrder>> byCustomer = orders.stream().filter(o -> o.customerId() != null)
                .collect(Collectors.groupingBy(PaidOrder::customerId));
        List<PaidOrder> anonymous = orders.stream().filter(o -> o.customerId() == null).toList();

        List<SalesAnalytics.CustomerTotal> all = new ArrayList<>();
        for (Map.Entry<Long, List<PaidOrder>> e : byCustomer.entrySet()) {
            BigDecimal revenue = sum(e.getValue());
            Instant last = e.getValue().stream().map(PaidOrder::soldAt).max(Comparator.naturalOrder()).orElse(null);
            all.add(new SalesAnalytics.CustomerTotal(e.getKey(), null, e.getValue().size(), revenue,
                    average(revenue, e.getValue().size()), last));
        }
        Comparator<SalesAnalytics.CustomerTotal> order = switch (sort == null ? CustomerSort.REVENUE : sort) {
            case ORDERS -> Comparator.comparingLong(SalesAnalytics.CustomerTotal::orders);
            case LAST_PURCHASE -> Comparator.comparing(SalesAnalytics.CustomerTotal::lastPurchaseAt);
            case REVENUE -> Comparator.comparing(SalesAnalytics.CustomerTotal::revenue);
        };
        all.sort(order.reversed().thenComparing(SalesAnalytics.CustomerTotal::customerId));

        int fromIndex = Math.min(page * size, all.size());
        List<SalesAnalytics.CustomerTotal> slice = all.subList(fromIndex, Math.min(fromIndex + size, all.size()));
        // Nomes só da página.
        Map<Long, String> names = customerRepository.findByIds(slice.stream()
                        .map(SalesAnalytics.CustomerTotal::customerId).toList()).stream()
                .collect(Collectors.toMap(Customer::id, Customer::nome, (a, b) -> a));
        List<SalesAnalytics.CustomerTotal> content = slice.stream()
                .map(c -> new SalesAnalytics.CustomerTotal(c.customerId(), names.get(c.customerId()), c.orders(),
                        c.revenue(), c.averageTicket(), c.lastPurchaseAt()))
                .toList();
        int totalPages = (int) Math.ceil(all.size() / (double) size);
        return new SalesAnalytics.CustomerRanking(content, page, size, all.size(), totalPages,
                new SalesAnalytics.AnonymousTotal(anonymous.size(), sum(anonymous)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesAnalytics.SlowMover> slowMovers(String warehouseCode, Instant from, Instant to,
            BigDecimal maxSold, int limit) {
        checkPeriod(from, to);
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (limit < 1 || limit > MAX_SLOW_MOVERS) {
            throw new InvalidReportPeriodException("limit deve estar entre 1 e " + MAX_SLOW_MOVERS);
        }
        BigDecimal threshold = maxSold == null ? BigDecimal.ZERO : maxSold;
        if (threshold.signum() < 0) {
            throw new InvalidReportPeriodException("maxSold não pode ser negativo");
        }
        return repository.slowMovers(warehouseCode.trim(), from, to, threshold, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesGoal getGoal(YearMonth month) {
        return repository.findGoal(month).orElseThrow(() -> new SalesGoalNotFoundException(month));
    }

    @Override
    @Transactional
    public void deleteGoal(YearMonth month) {
        if (!repository.deleteGoal(month)) {
            throw new SalesGoalNotFoundException(month);
        }
    }

    @Override
    @Transactional
    public SalesGoal saveGoal(YearMonth month, BigDecimal amount, String username) {
        return repository.saveGoal(new SalesGoal(month, amount, username, clock.instant()));
    }

    /** Mesmo teto e mesmos erros do summary (400 INVALID_REPORT_PERIOD). */
    static void checkPeriod(Instant from, Instant to) {
        OrderReportService.checkPeriod(from, to);
    }

    private static BigDecimal sum(List<PaidOrder> orders) {
        return orders.stream().map(PaidOrder::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal total, long count) {
        return count == 0 ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static String name(SalesChannel channel) {
        return channel == null ? null : channel.name();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
