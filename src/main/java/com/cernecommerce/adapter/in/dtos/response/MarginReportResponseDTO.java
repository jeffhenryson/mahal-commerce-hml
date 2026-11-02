package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Relatório de margem agregado do período (FIN-F003, GET /financeiro/margem). */
@Data
public class MarginReportResponseDTO {

    @Schema(description = "Quantidade de itens com costPrice congelado usados no agregado — "
            + "itens legados sem custo são excluídos, não contados como margem zero.")
    private long itemsConsidered;

    private BigDecimal totalRevenueNet;
    private BigDecimal totalCost;
    private BigDecimal totalMargin;
    private BigDecimal marginPercent;
    private List<MarginByProductResponseDTO> topProductsByMargin;

    @Schema(description = "Desconto de promoção por quantidade (\"leve 4 por R$ 10\") no período — a linha \"Promoções\"")
    private BigDecimal promotionDiscountTotal;

    @Schema(description = "Demais descontos do período: kit montável e desconto manual (no balcão o kit chega "
            + "somado ao desconto da linha, por isso os dois vêm juntos)")
    private BigDecimal otherDiscountTotal;
}
