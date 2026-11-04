package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** V186 — saldo de cashback do cliente no app e a prévia de resgate para o carrinho. */
@Data
public class ShopCashbackResponseDTO {
    @Schema(description = "Saldo disponível para usar agora")
    private BigDecimal available;
    @Schema(description = "Cashback ainda em carência (vira disponível depois)")
    private BigDecimal pending;
    @Schema(description = "Parte do saldo que vence nos próximos 30 dias")
    private BigDecimal expiringSoon;
    @Schema(description = "Saldo mínimo para poder usar")
    private BigDecimal minimumBalance;
    @Schema(description = "Teto do resgate, em % da compra")
    private BigDecimal capPercent;
    @Schema(description = "Teto em reais para esta compra")
    private BigDecimal capAmount;
    @Schema(description = "Máximo que pode usar nesta compra (0 se não pode)")
    private BigDecimal maxRedeemable;
    @Schema(description = "Por que não pode usar (CASHBACK_*), ou null")
    private String reason;
}
