package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, Long> {

    /** Desconto de promoção por quantidade do período, mesmo filtro de {@link #findTopProducts}. */
    @Query("""
            SELECT COALESCE(SUM(oi.promotionDiscountAmount), 0)
            FROM OrderItemEntity oi
            JOIN oi.order o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND (:warehouseCode IS NULL OR o.warehouseCode = :warehouseCode)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            """)
    BigDecimal sumPromotionDiscount(@Param("channel") String channel, @Param("status") String status,
            @Param("customerId") Long customerId,
            @Param("warehouseCode") String warehouseCode, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Top N produtos por receita no período (GET /orders/summary). {@code LEFT JOIN} por
     * <b>valor</b> contra {@code product.sku} — não há FK entre {@code order_item} e
     * {@code product} (o item congela o sku no instante da venda; o produto pode ter sido
     * renomeado ou excluído depois). SKU órfão aparece com o próprio código como nome, nunca é
     * descartado: a venda aconteceu, e omiti-la subestimaria o produto mais vendido do período.
     * Mesma restrição de "pagamento confirmado, exceto reembolsado" das outras agregações de
     * receita — ver {@link OrderJpaRepository#findRevenueTotals}.
     */
    @Query("""
            SELECT oi.sku AS sku, COALESCE(p.name, oi.sku) AS productName,
                   SUM(oi.quantity) AS quantitySold,
                   COALESCE(SUM(oi.quantity * oi.unitPrice - oi.discountAmount), 0) AS revenue
            FROM OrderItemEntity oi
            JOIN oi.order o
            LEFT JOIN ProductEntity p ON p.sku = oi.sku
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND (:warehouseCode IS NULL OR o.warehouseCode = :warehouseCode)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            GROUP BY oi.sku, p.name
            ORDER BY revenue DESC
            """)
    List<TopProductProjection> findTopProducts(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("warehouseCode") String warehouseCode,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /**
     * Igual a {@link #findTopProducts}, ordenado por quantidade vendida em vez de receita
     * ({@code GET /orders/analytics/top-products?sortBy=quantity}).
     */
    @Query("""
            SELECT oi.sku AS sku, COALESCE(p.name, oi.sku) AS productName,
                   SUM(oi.quantity) AS quantitySold,
                   COALESCE(SUM(oi.quantity * oi.unitPrice - oi.discountAmount), 0) AS revenue
            FROM OrderItemEntity oi
            JOIN oi.order o
            LEFT JOIN ProductEntity p ON p.sku = oi.sku
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND (:warehouseCode IS NULL OR o.warehouseCode = :warehouseCode)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            GROUP BY oi.sku, p.name
            ORDER BY quantitySold DESC
            """)
    List<TopProductProjection> findTopProductsByQuantity(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("warehouseCode") String warehouseCode,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    interface TopProductProjection {
        String getSku();
        String getProductName();
        BigDecimal getQuantitySold();
        BigDecimal getRevenue();
    }

    /**
     * Totais de margem do período (FIN-F003, {@code GET /financeiro/margem}). Mesmo filtro de
     * status pago de {@link #findTopProducts} e mesmo {@code warehouseCode} de {@code Order}
     * (não existe em {@code OrderItem}, daí o {@code JOIN oi.order o}).
     *
     * <p>{@code AND oi.costPrice IS NOT NULL} exclui itens legados sem custo congelado do
     * agregado — somá-los como zero inflaria a margem, e é a mesma regra de
     * {@code OrderItem.marginAmount()} (retorna {@code null} quando falta custo).</p>
     */
    @Query("""
            SELECT COUNT(oi) AS itemsConsidered,
                   COALESCE(SUM(oi.quantity * oi.unitPrice - oi.discountAmount), 0) AS revenue,
                   COALESCE(SUM(oi.quantity * oi.costPrice), 0) AS cost,
                   COALESCE(SUM(oi.quantity * oi.unitPrice - oi.discountAmount - oi.quantity * oi.costPrice), 0) AS margin,
                   COALESCE(SUM(oi.discountAmount), 0) AS discount,
                   COALESCE(SUM(oi.promotionDiscountAmount), 0) AS promotionDiscount
            FROM OrderItemEntity oi
            JOIN oi.order o
            WHERE (:channel       IS NULL OR o.channel       = :channel)
              AND (:warehouseCode IS NULL OR o.warehouseCode = :warehouseCode)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
              AND oi.costPrice IS NOT NULL
            """)
    MarginTotalsProjection findMarginTotals(@Param("channel") String channel,
            @Param("warehouseCode") String warehouseCode, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Top N produtos por margem no período, mesmo molde e mesma exclusão de itens sem custo de
     * {@link #findMarginTotals}.
     */
    @Query("""
            SELECT oi.sku AS sku, COALESCE(p.name, oi.sku) AS productName,
                   SUM(oi.quantity) AS quantitySold,
                   COALESCE(SUM(oi.quantity * oi.unitPrice - oi.discountAmount - oi.quantity * oi.costPrice), 0) AS margin
            FROM OrderItemEntity oi
            JOIN oi.order o
            LEFT JOIN ProductEntity p ON p.sku = oi.sku
            WHERE (:channel       IS NULL OR o.channel       = :channel)
              AND (:warehouseCode IS NULL OR o.warehouseCode = :warehouseCode)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
              AND oi.costPrice IS NOT NULL
            GROUP BY oi.sku, p.name
            ORDER BY margin DESC
            """)
    List<TopProductByMarginProjection> findTopProductsByMargin(@Param("channel") String channel,
            @Param("warehouseCode") String warehouseCode, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);

    interface MarginTotalsProjection {
        long getItemsConsidered();
        BigDecimal getRevenue();
        BigDecimal getCost();
        BigDecimal getMargin();
        BigDecimal getDiscount();
        BigDecimal getPromotionDiscount();
    }

    /**
     * Números de uma promoção por quantidade no período ({@code GET /estoque/promotions/quantity/{id}/stats}).
     * Mesmo filtro de pedido pago da margem.
     */
    @Query("""
            SELECT COUNT(DISTINCT o.id) AS vendas,
                   COALESCE(SUM(oi.quantity), 0) AS unidades,
                   COALESCE(SUM(oi.promotionDiscountAmount), 0) AS descontoTotal
            FROM OrderItemEntity oi
            JOIN oi.order o
            WHERE oi.promotionId = :promotionId
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            """)
    PromotionStatsProjection findPromotionStats(@Param("promotionId") Long promotionId,
            @Param("from") Instant from, @Param("to") Instant to);

    interface PromotionStatsProjection {
        long getVendas();
        BigDecimal getUnidades();
        BigDecimal getDescontoTotal();
    }

    interface TopProductByMarginProjection {
        String getSku();
        String getProductName();
        BigDecimal getQuantitySold();
        BigDecimal getMargin();
    }
}
