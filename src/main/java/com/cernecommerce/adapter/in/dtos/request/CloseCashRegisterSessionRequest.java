package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseCashRegisterSessionRequest {

    @NotNull
    @DecimalMin(value = "0.0")
    @Schema(description = "O que foi contado de fato na gaveta. Divergir do esperado NÃO impede o "
            + "fechamento — a diferença é registrada, como no fechamento de um balanço de inventário.",
            example = "495.00")
    private BigDecimal countedAmount;

    @Size(max = 500)
    @Schema(description = "Motivo/observação do fechamento, opcional. Usado quando admin ou dev encerra "
            + "o caixa de outro operador.", example = "Operador saiu sem fechar o caixa")
    private String notes;

    @DecimalMin(value = "0.0")
    @Schema(description = "PDV-F056 — parte do contado que fica na gaveta como troco do próximo turno, "
            + "opcional. Não pode passar do contado. Não altera esperado nem diferença.", example = "100.00")
    private BigDecimal leftInDrawerAmount;
}
