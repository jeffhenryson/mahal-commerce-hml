package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Parâmetros do programa de cashback ajustados pelo admin. Os quatro são obrigatórios. */
@Data
public class CashbackSettingsRequest {

    @Schema(description = "Dias entre o pagamento e o cashback ficar disponível. Vale para ganhos novos.", example = "7")
    @NotNull
    @Min(0)
    @Max(365)
    private Integer carenciaDias;

    @Schema(description = "Dias, a partir da liberação, até o cashback vencer. Vale para ganhos novos.", example = "180")
    @NotNull
    @Min(1)
    @Max(3650)
    private Integer expiracaoDias;

    @Schema(description = "Obsoleto: o resgate não tem mais teto percentual (o limite é o saldo disponível e o "
            + "líquido dos itens elegíveis). Aceito e devolvido só por compatibilidade — reenvie o valor lido.",
            example = "50", deprecated = true)
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal resgateTetoPercent;

    @Schema(description = "Obsoleto: o resgate não tem mais saldo mínimo. Aceito e devolvido só por "
            + "compatibilidade — reenvie o valor lido.", example = "50.00", deprecated = true)
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal resgateMinimoSaldo;
}
