package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashbackRedemptionQuoteResponseDTO {
    @Schema(description = "Saldo de cashback disponível agora")
    private BigDecimal available;
    @Schema(description = "Saldo mínimo configurado para resgatar")
    private BigDecimal minimumBalance;
    @Schema(description = "Teto configurado, em % da mercadoria")
    private BigDecimal capPercent;
    @Schema(description = "Teto em reais para esta compra")
    private BigDecimal capAmount;
    @Schema(description = "Máximo que pode ser resgatado nesta compra (0 se não pode)")
    private BigDecimal maxRedeemable;
    @Schema(description = "Por que não pode resgatar (CASHBACK_CUSTOMER_NOT_ELIGIBLE, CASHBACK_BELOW_MINIMUM_BALANCE, "
            + "CASHBACK_INSUFFICIENT_BALANCE, CASHBACK_REDEMPTION_DISABLED, CASHBACK_CUSTOMER_REQUIRED), ou null")
    private String reason;
}
