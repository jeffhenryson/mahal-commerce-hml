package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Resultado de uma transferência entre depósitos (EST-F039): os dois saldos do SKU depois do
 * movimento e o custo levado ao destino.
 */
public record StockTransferResponseDTO(
        String sku,
        @Schema(description = "Saldo do SKU na origem depois da saída", example = "7")
        BigDecimal fromBalance,
        @Schema(description = "Saldo do SKU no destino depois da entrada", example = "5")
        BigDecimal toBalance,
        @Schema(description = "Custo unitário (custo médio da origem; sem ele, o custo do cadastro). Nulo sem custo nenhum",
                example = "32.00")
        BigDecimal unitCost,
        @Schema(description = "unitCost × quantidade", example = "96.00")
        BigDecimal totalCost) {
}
