package com.cernecommerce.core.domain.model.pedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resumo agregado de vendas de um período (GET /orders/summary).
 *
 * <p>{@code ordersByStatus} mostra a distribuição completa dos pedidos do período, incluindo
 * {@link OrderStatus#CRIADO}/{@link OrderStatus#AGUARDANDO_PAGAMENTO}/{@link OrderStatus#CANCELADO}.
 * Já {@code totalRevenueNet}, {@code totalRevenueGross}, {@code averageTicket},
 * {@code revenueByChannel} e {@code dailyRevenue} só contam pedidos com pagamento confirmado —
 * {@link OrderStatus#PAGO} em diante, exceto {@link OrderStatus#REEMBOLSADO} — porque
 * {@link OrderStatus#CANCELADO}/{@link OrderStatus#REEMBOLSADO} não zeram os totais do pedido
 * (dinheiro nunca entrou, ou voltou), e contá-los aqui infla a receita com dinheiro que não existe.</p>
 */
public record OrderSummary(
        long totalOrders,
        BigDecimal totalRevenueNet,
        BigDecimal totalRevenueGross,
        BigDecimal averageTicket,
        Map<SalesChannel, BigDecimal> revenueByChannel,
        Map<OrderStatus, Long> ordersByStatus,
        BigDecimal cancelledOrRefundedRate,
        List<DailyRevenue> dailyRevenue,
        List<TopProduct> topProducts,
        BigDecimal promotionDiscountTotal,
        BigDecimal deliveryFeeTotal,
        BigDecimal serviceFeeTotal) {

    /**
     * {@code promotionDiscountTotal}: desconto de promoção por quantidade do período ("Promoções").
     * {@code deliveryFeeTotal} e {@code serviceFeeTotal}: frete e taxa de serviço dos mesmos pedidos de
     * {@code totalRevenueNet}. Ficam fora do líquido (PDV-F015), mas o cliente paga, então a soma das formas
     * de pagamento é líquido + frete + taxa.
     */
    public OrderSummary {
        promotionDiscountTotal = promotionDiscountTotal == null ? BigDecimal.ZERO : promotionDiscountTotal;
        deliveryFeeTotal = deliveryFeeTotal == null ? BigDecimal.ZERO : deliveryFeeTotal;
        serviceFeeTotal = serviceFeeTotal == null ? BigDecimal.ZERO : serviceFeeTotal;
    }

    public OrderSummary(long totalOrders, BigDecimal totalRevenueNet, BigDecimal totalRevenueGross,
            BigDecimal averageTicket, Map<SalesChannel, BigDecimal> revenueByChannel,
            Map<OrderStatus, Long> ordersByStatus, BigDecimal cancelledOrRefundedRate,
            List<DailyRevenue> dailyRevenue, List<TopProduct> topProducts, BigDecimal promotionDiscountTotal) {
        this(totalOrders, totalRevenueNet, totalRevenueGross, averageTicket, revenueByChannel, ordersByStatus,
                cancelledOrRefundedRate, dailyRevenue, topProducts, promotionDiscountTotal, BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    public OrderSummary(long totalOrders, BigDecimal totalRevenueNet, BigDecimal totalRevenueGross,
            BigDecimal averageTicket, Map<SalesChannel, BigDecimal> revenueByChannel,
            Map<OrderStatus, Long> ordersByStatus, BigDecimal cancelledOrRefundedRate,
            List<DailyRevenue> dailyRevenue, List<TopProduct> topProducts) {
        this(totalOrders, totalRevenueNet, totalRevenueGross, averageTicket, revenueByChannel, ordersByStatus,
                cancelledOrRefundedRate, dailyRevenue, topProducts, BigDecimal.ZERO);
    }

    public record DailyRevenue(LocalDate date, BigDecimal revenue, long orderCount) {}

    /**
     * {@code productName} cai para o próprio {@code sku} quando o produto foi renomeado ou
     * excluído depois da venda — não há FK entre item de pedido e produto, o item congela o sku
     * no instante da venda.
     */
    public record TopProduct(String sku, String productName, BigDecimal quantitySold, BigDecimal revenue) {}
}
