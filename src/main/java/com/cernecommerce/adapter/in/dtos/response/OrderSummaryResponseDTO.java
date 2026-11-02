package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Resumo agregado de vendas do período (GET /orders/summary). */
@Data
public class OrderSummaryResponseDTO {

    private long totalOrders;

    @Schema(description = "Soma de netAmount só de pedidos com pagamento confirmado, exceto "
            + "reembolsado (PAGO/SEPARADO/ENVIADO/ENTREGUE/CONCLUIDO).")
    private BigDecimal totalRevenueNet;

    private BigDecimal totalRevenueGross;
    private BigDecimal averageTicket;

    @Schema(description = "Receita líquida por canal (BALCAO/MARKETPLACE), mesma restrição de status de totalRevenueNet.")
    private Map<String, BigDecimal> revenueByChannel;

    @Schema(description = "Distribuição completa de pedidos por status no período — sem a restrição "
            + "de pagamento confirmado, inclui CRIADO/AGUARDANDO_PAGAMENTO/CANCELADO.")
    private Map<String, Long> ordersByStatus;

    private BigDecimal cancelledOrRefundedRate;
    private List<DailyRevenueResponseDTO> dailyRevenue;
    private List<TopProductResponseDTO> topProducts;

    @Schema(description = "Desconto de promoção por quantidade (\"leve 4 por R$ 10\") no período.")
    private java.math.BigDecimal promotionDiscountTotal;
}
