package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** PDV-F056 — desempenho do operador nos últimos caixas fechados (abertura do caixa). */
@Data
public class OperatorSessionStatsResponseDTO {

    private String operator;

    @Schema(description = "Quantos caixas fechados entraram na conta (até o limit pedido).")
    private int sessionCount;

    @Schema(description = "Soma do totalReceived do resumo de cada caixa — o que foi vendido, todas as formas.")
    private BigDecimal totalSold;

    @Schema(description = "totalSold ÷ sessionCount; zero sem caixas.")
    private BigDecimal averageSold;

    @Schema(description = "Quantos desses caixas fecharam com diferença entre contado e esperado.")
    private int divergenceCount;

    @Schema(description = "Soma das diferenças (contado − esperado). Negativa é falta acumulada.")
    private BigDecimal totalDifference;

    @Schema(description = "Troco deixado na gaveta no caixa fechado mais recente, quando informado — a sugestão "
            + "do \"Usar troco anterior\".")
    private BigDecimal lastLeftInDrawerAmount;
}
