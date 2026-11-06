package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.pedido.MarginSummary;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.out.pedido.OrderReportRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class OrderReportRepositoryImpl implements OrderReportRepository {

    /** Fuso da loja: o "dia" da série é o do calendário de quem vende, não o do banco. */
    static final java.time.ZoneId STORE_ZONE = java.time.ZoneId.of("America/Sao_Paulo");

    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

    public OrderReportRepositoryImpl(OrderJpaRepository orderJpaRepository,
            OrderItemJpaRepository orderItemJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderItemJpaRepository = orderItemJpaRepository;
    }

    @Override
    public OrderSummary summarize(SalesChannel channel, OrderStatus status, Long customerId, String warehouseCode,
            Instant from, Instant to, int topProductsLimit) {
        String channelName = channel == null ? null : channel.name();
        String statusName = status == null ? null : status.name();

        List<OrderJpaRepository.StatusCountProjection> statusCounts = orderJpaRepository
                .countByStatus(channelName, statusName, customerId, warehouseCode, from, to);
        Map<OrderStatus, Long> ordersByStatus = statusCounts.stream()
                .collect(Collectors.toMap(p -> OrderStatus.valueOf(p.getStatus()),
                        OrderJpaRepository.StatusCountProjection::getCount));
        long totalOrders = ordersByStatus.values().stream().mapToLong(Long::longValue).sum();
        long cancelledOrRefunded = ordersByStatus.getOrDefault(OrderStatus.CANCELADO, 0L)
                + ordersByStatus.getOrDefault(OrderStatus.REEMBOLSADO, 0L);
        BigDecimal cancelledOrRefundedRate = totalOrders == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelledOrRefunded)
                        .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP);

        OrderJpaRepository.HeaderTotalsProjection totals = orderJpaRepository
                .findRevenueTotals(channelName, statusName, customerId, warehouseCode, from, to);
        BigDecimal averageTicket = totals.getOrderCount() == 0 ? BigDecimal.ZERO
                : totals.getTotalRevenueNet()
                        .divide(BigDecimal.valueOf(totals.getOrderCount()), Money.MONEY_SCALE, Money.ROUNDING);

        Map<SalesChannel, BigDecimal> revenueByChannel = orderJpaRepository
                .findRevenueByChannel(channelName, statusName, customerId, warehouseCode, from, to).stream()
                .collect(Collectors.toMap(p -> SalesChannel.valueOf(p.getChannel()),
                        OrderJpaRepository.ChannelRevenueProjection::getRevenue));

        // Pendência 9 — dia local da loja, agrupado aqui (ver findPaidInstantsAndAmounts).
        java.util.TreeMap<java.time.LocalDate, BigDecimal[]> byDay = new java.util.TreeMap<>();
        for (Object[] row : orderJpaRepository.findPaidInstantsAndAmounts(channelName, statusName, customerId,
                warehouseCode, from, to)) {
            java.time.LocalDate day = ((Instant) row[0]).atZone(STORE_ZONE).toLocalDate();
            BigDecimal[] acc = byDay.computeIfAbsent(day, d -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add((BigDecimal) row[1]);
            acc[1] = acc[1].add(BigDecimal.ONE);
        }
        List<OrderSummary.DailyRevenue> dailyRevenue = byDay.entrySet().stream()
                .map(e -> new OrderSummary.DailyRevenue(e.getKey(), e.getValue()[0], e.getValue()[1].longValue()))
                .toList();

        List<OrderSummary.TopProduct> topProducts = orderItemJpaRepository
                .findTopProducts(channelName, statusName, customerId, warehouseCode, from, to,
                        PageRequest.of(0, topProductsLimit))
                .stream()
                .map(p -> new OrderSummary.TopProduct(p.getSku(), p.getProductName(), p.getQuantitySold(),
                        p.getRevenue()))
                .toList();

        return new OrderSummary(totalOrders, totals.getTotalRevenueNet(), totals.getTotalRevenueGross(),
                averageTicket, revenueByChannel, ordersByStatus, cancelledOrRefundedRate,
                dailyRevenue, topProducts,
                orderItemJpaRepository.sumPromotionDiscount(channelName, statusName, customerId, warehouseCode, from, to),
                totals.getDeliveryFeeTotal(), totals.getServiceFeeTotal());
    }

    @Override
    public List<OrderSummary.TopProduct> findTopProducts(SalesChannel channel, OrderStatus status,
            Long customerId, Instant from, Instant to, int limit, boolean byQuantity) {
        String channelName = channel == null ? null : channel.name();
        String statusName = status == null ? null : status.name();
        Pageable pageable = PageRequest.of(0, limit);

        List<OrderItemJpaRepository.TopProductProjection> projections = byQuantity
                ? orderItemJpaRepository.findTopProductsByQuantity(channelName, statusName, customerId, null,
                        from, to, pageable)
                : orderItemJpaRepository.findTopProducts(channelName, statusName, customerId, null, from, to,
                        pageable);

        return projections.stream()
                .map(p -> new OrderSummary.TopProduct(p.getSku(), p.getProductName(), p.getQuantitySold(),
                        p.getRevenue()))
                .toList();
    }

    @Override
    public MarginSummary summarizeMargin(SalesChannel channel, String warehouseCode, Instant from, Instant to,
            int topProductsLimit) {
        String channelName = channel == null ? null : channel.name();

        OrderItemJpaRepository.MarginTotalsProjection totals = orderItemJpaRepository
                .findMarginTotals(channelName, warehouseCode, from, to);
        BigDecimal marginPercent = totals.getRevenue().signum() == 0 ? BigDecimal.ZERO
                : totals.getMargin().divide(totals.getRevenue(), Money.INTERMEDIATE_SCALE, Money.ROUNDING)
                        .multiply(Money.HUNDRED)
                        .setScale(Money.PERCENT_SCALE, Money.ROUNDING);

        List<MarginSummary.MarginByProduct> topProductsByMargin = orderItemJpaRepository
                .findTopProductsByMargin(channelName, warehouseCode, from, to, PageRequest.of(0, topProductsLimit))
                .stream()
                .map(p -> new MarginSummary.MarginByProduct(p.getSku(), p.getProductName(), p.getQuantitySold(),
                        p.getMargin()))
                .toList();

        return new MarginSummary(totals.getItemsConsidered(), totals.getRevenue(), totals.getCost(),
                totals.getMargin(), marginPercent, topProductsByMargin, totals.getPromotionDiscount(),
                totals.getDiscount().subtract(totals.getPromotionDiscount()));
    }
}
