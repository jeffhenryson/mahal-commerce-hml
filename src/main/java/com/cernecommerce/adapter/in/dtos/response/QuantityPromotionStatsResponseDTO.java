package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Resultado de uma promoção por quantidade no período (pedidos pagos)")
public class QuantityPromotionStatsResponseDTO {
    @Schema(description = "Pedidos com a promoção")
    private long vendas;
    @Schema(description = "Grupos fechados (unidades ÷ quantity)")
    private long grupos;
    private long unidades;
    private BigDecimal descontoTotal;
}
