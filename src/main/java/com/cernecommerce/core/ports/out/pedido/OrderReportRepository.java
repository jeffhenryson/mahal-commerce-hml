package com.cernecommerce.core.ports.out.pedido;

import com.cernecommerce.core.domain.model.pedido.MarginSummary;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;

import java.time.Instant;
import java.util.List;

public interface OrderReportRepository {

    /**
     * Agrega os pedidos do período em um {@link OrderSummary}. {@code topProductsLimit} é o N do
     * top produtos por receita.
     */
    default OrderSummary summarize(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int topProductsLimit) {
        return summarize(channel, status, customerId, null, from, to, topProductsLimit);
    }

    /**
     * Pendência 9 — com {@code warehouseCode} opcional (o mesmo filtro da margem). A série diária é
     * agrupada no dia de {@code America/Sao_Paulo}.
     */
    OrderSummary summarize(SalesChannel channel, OrderStatus status, Long customerId, String warehouseCode,
            Instant from, Instant to, int topProductsLimit);

    /**
     * Agrega a margem ({@code OrderItem.marginAmount()}) dos itens do período em um
     * {@link MarginSummary} (FIN-F003, {@code GET /financeiro/margem}). {@code warehouseCode} é
     * opcional (filtro de depósito/loja, vem de {@code Order.warehouseCode}). Mesma restrição de
     * status de {@link #summarize} (pagamento confirmado, exceto reembolsado) e mesma exclusão de
     * itens sem {@code costPrice} congelado.
     */
    MarginSummary summarizeMargin(SalesChannel channel, String warehouseCode, Instant from, Instant to,
            int topProductsLimit);

    /**
     * Top N produtos do período, por receita ou por quantidade vendida
     * (GET /orders/analytics/top-products). {@code from}/{@code to} nunca chegam nulos aqui —
     * "histórico completo" é resolvido pelo chamador com os limites concretos, para não reabrir o
     * bug de bind nulo de {@code Instant} em JPQL já documentado em
     * {@code OrderRepositoryImpl#findAll}.
     */
    List<OrderSummary.TopProduct> findTopProducts(SalesChannel channel, OrderStatus status,
            Long customerId, Instant from, Instant to, int limit, boolean byQuantity);
}
