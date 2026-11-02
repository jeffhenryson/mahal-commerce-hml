package com.cernecommerce.core.domain.model.pedido;

import java.math.BigDecimal;
import java.util.List;

/**
 * Relatório de margem agregado de um período (FIN-F003, {@code GET /financeiro/margem}).
 *
 * <p>Mesma base de dados de {@link OrderSummary}, filtrada pela mesma regra de pagamento
 * confirmado (PAGO em diante, exceto REEMBOLSADO), mas agregando {@link OrderItem#marginAmount()}
 * em vez de receita. Itens sem {@code costPrice} congelado (pedidos legados pré-migração) são
 * <b>excluídos</b> do agregado — {@code itemsConsidered} deixa isso visível em vez de fingir que
 * a margem desses itens é zero.</p>
 */
public record MarginSummary(
        long itemsConsidered,
        BigDecimal totalRevenueNet,
        BigDecimal totalCost,
        BigDecimal totalMargin,
        BigDecimal marginPercent,
        List<MarginByProduct> topProductsByMargin,
        BigDecimal promotionDiscountTotal,
        BigDecimal otherDiscountTotal) {

    /**
     * Descontos do período separados por origem: {@code promotionDiscountTotal} é a promoção por
     * quantidade (regra da loja), e {@code otherDiscountTotal} junta kit montável e desconto manual.
     * No balcão o kit chega somado ao desconto da linha, por isso os dois não se separam.
     */
    public MarginSummary {
        promotionDiscountTotal = promotionDiscountTotal == null ? BigDecimal.ZERO : promotionDiscountTotal;
        otherDiscountTotal = otherDiscountTotal == null ? BigDecimal.ZERO : otherDiscountTotal;
    }

    public MarginSummary(long itemsConsidered, BigDecimal totalRevenueNet, BigDecimal totalCost,
            BigDecimal totalMargin, BigDecimal marginPercent, List<MarginByProduct> topProductsByMargin) {
        this(itemsConsidered, totalRevenueNet, totalCost, totalMargin, marginPercent, topProductsByMargin,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * {@code productName} cai para o próprio {@code sku} quando o produto foi renomeado ou
     * excluído depois da venda — mesma regra de {@link OrderSummary.TopProduct}.
     */
    public record MarginByProduct(String sku, String productName, BigDecimal quantitySold, BigDecimal margin) {}
}
