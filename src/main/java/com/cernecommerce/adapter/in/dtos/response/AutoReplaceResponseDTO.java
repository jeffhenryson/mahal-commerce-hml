package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** "Repor sozinha" — a configuração que vale para o SKU pedido (herdada do produto base). */
public record AutoReplaceResponseDTO(
        @Schema(description = "O SKU pedido (variação ou produto base)", example = "ZOMO-MENTA") String sku,
        @Schema(description = "Reposição automática ligada", example = "true") boolean enabled,
        @Schema(description = "Limite de usos; nulo = sessionsPerUnit da lata", example = "6", nullable = true)
        Integer limit) {
}
