package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** EST-F037 — o rendimento real de um sabor, tirado das latas fechadas. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenPackageYieldResponseDTO {

    @Schema(description = "SKU do sabor (a variação)", example = "ZOMO-MENTA")
    private String sku;

    @Schema(example = "Zomo Menta")
    private String productName;

    @Schema(description = "Latas fechadas no período, com ao menos um uso (FINISHED, REPLACED e as EXHAUSTED antigas)",
            example = "5")
    private long closedCount;

    @Schema(description = "Média de sessões por lata, com uma casa", example = "4.6")
    private BigDecimal avgSessions;

    @Schema(description = "Soma das sessões dessas latas", example = "23")
    private long totalSessions;

    @Schema(description = "O sessionsPerUnit atual do produto — a estimativa; nulo sem cadastro", example = "5")
    private Integer estimate;
}
