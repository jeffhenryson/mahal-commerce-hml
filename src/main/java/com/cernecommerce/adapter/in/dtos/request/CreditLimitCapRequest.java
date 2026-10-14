package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** PDV-F046 — novo teto do limite de "Marcar" que o vendedor pode dar. */
@Data
public class CreditLimitCapRequest {

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Teto por canal e para o total. 0 impede o vendedor de dar limite.", example = "150.00")
    private BigDecimal maxCreditLimit;
}
