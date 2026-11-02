package com.cernecommerce.core.domain.model.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Agregados da Análise de Vendas (pendência 9). Todos contam os mesmos pedidos de
 * {@code totalRevenueNet} do summary (pagamento confirmado, sem CANCELADO/REEMBOLSADO), pelo
 * {@code netAmount}, e agrupam dia e hora em {@code America/Sao_Paulo}.
 */
public final class SalesAnalytics {

    private SalesAnalytics() {
    }

    /** @param weekday ISO, 1 = segunda … 7 = domingo; @param hour 0 a 23, no fuso local */
    public record HeatmapCell(int weekday, int hour, BigDecimal revenue, long orders) {
    }

    /** @param orders pedidos distintos que usaram o método */
    public record PaymentMethodTotal(String method, BigDecimal amount, long orders) {
    }

    /** @param operatorUserId nulo na linha dos pedidos sem caixa (pagos online) */
    public record OperatorTotal(Long operatorUserId, String operatorName, BigDecimal revenue, long orders,
            BigDecimal averageTicket) {
    }

    public record CustomerTotal(Long customerId, String customerName, long orders, BigDecimal revenue,
            BigDecimal averageTicket, Instant lastPurchaseAt) {
    }

    public record AnonymousTotal(long orders, BigDecimal revenue) {
    }

    public record CustomerRanking(List<CustomerTotal> content, int page, int size, long totalElements,
            int totalPages, AnonymousTotal anonymous) {
    }

    /** @param lastSoldAt última venda em qualquer data (não só no período); nulo se nunca vendeu */
    public record SlowMover(String sku, String productName, BigDecimal stock, BigDecimal unitCost,
            BigDecimal quantitySold, Instant lastSoldAt) {

        public BigDecimal stockValue() {
            return stock.multiply(unitCost);
        }
    }

    /** Linha crua de pedido pago, para agrupar em Java no fuso da loja. */
    public record PaidOrder(Long id, Instant soldAt, BigDecimal netAmount, Long customerId, Long sessionId) {
    }

    public enum CustomerSort { REVENUE, ORDERS, LAST_PURCHASE }
}
