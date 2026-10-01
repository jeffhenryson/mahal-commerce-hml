package com.cernecommerce.infra.handler;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** 409 CREDIT_LIMIT_EXCEEDED do "Marcar" (CRM-F010): o corpo padrão mais o limite e o saldo. */
@Schema(description = "O marcado passaria do limite de crédito do cliente")
public record CreditLimitError(
        String message,
        @Schema(description = "Sempre CREDIT_LIMIT_EXCEEDED") String errorCode,
        Instant timestamp,
        String path,
        String traceId,
        @Schema(description = "Limite efetivo do cliente") BigDecimal limit,
        @Schema(description = "Saldo marcado em aberto") BigDecimal openBalance,
        @Schema(description = "Quanto ainda cabe no limite") BigDecimal available
) {
}
