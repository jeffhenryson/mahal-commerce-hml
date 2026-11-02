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

    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

    public OrderReportRepositoryImpl(OrderJpaRepository orderJpaRepository,
            OrderItemJpaRepository orderItemJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderItemJpaRepository = orderItemJpaRepository;
    }

    @Override
    public OrderSummary summarize(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int topProductsLimit) {
        String channelName = channel == null ? null : channel.name();
        String statusName = status == null ? null : status.name();

        List<OrderJpaRepository.StatusCountProjection> statusCounts = orderJpaRepository
                .countByStatus(channelName, statusName, customerId, from, to);
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
                .findRevenueTotals(channelName, statusName, customerId, from, to);
        BigDecimal averageTicket = totals.getOrderCount() == 0 ? BigDecimal.ZERO
                : totals.getTotalRevenueNet()
                        .divide(BigDecimal.valueOf(totals.getOrderCount()), Money.MONEY_SCALE, Money.ROUNDING);

        Map<SalesChannel, BigDecimal> revenueByChannel = orderJpaRepository
                .findRevenueByChannel(channelName, statusName, customerId, from, to).stream()
                .collect(Collectors.toMap(p -> SalesChannel.valueOf(p.getChannel()),
                        OrderJpaRepository.ChannelRevenueProjection::getRevenue));

        List<OrderSummary.DailyRevenue> dailyRevenue = orderJpaRepository
                .findDailyRevenue(channelName, statusName, customerId, from, to).stream()
                .map(p -> new OrderSummary.DailyRevenue(p.getDay(), p.getRevenue(), p.getOrderCount()))
                .toList();

        List<OrderSummary.TopProduct> topProducts = orderItemJpaRepository
                .findTopProducts(channelName, statusName, customerId, from, to,
                        PageRequest.of(0, topProductsLimit))
                .stream()
                .map(p -> new OrderSummary.TopProduct(p.getSku(), p.getProductName(), p.getQuantitySold(),
                        p.getRevenue()))
                .toList();

        return new OrderSummary(totalOrders, totals.getTotalRevenueNet(), totals.getTotalRevenueGross(),
                averageTicket, revenueByChannel, ordersByStatus, cancelledOrRefundedRate,
                dailyRevenue, topProducts,
                orderItemJpaRepository.sumPromotionDiscount(channelName, statusName, customerId, from, to));
    }

    @Override
    public List<OrderSummary.TopProduct> findTopProducts(SalesChannel channel, OrderStatus status,
            Long customerId, Instant from, Instant to, int limit, boolean byQuantity) {
        String channelName = channel == null ? null : channel.name();
        String statusName = status == null ? null : status.name();
        Pageable pageable = PageRequest.of(0, limit);

        List<OrderItemJpaRepository.TopProductProjection> projections = byQuantity
                ? orderItemJpaRepository.findTopProductsByQuantity(channelName, statusName, customerId,
                        from, to, pageable)
                : orderItemJpaRepository.findTopProducts(channelName, statusName, customerId, from, to,
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
