package com.cernecommerce.infra.handler;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** 409 CUSTOMER_HAS_OVERDUE do "Marcar" (CRM-F010): o corpo padrão mais o saldo vencido. */
@Schema(description = "O cliente tem marcado vencido e não pode marcar de novo")
public record OverdueError(
        String message,
        @Schema(description = "Sempre CUSTOMER_HAS_OVERDUE") String errorCode,
        Instant timestamp,
        String path,
        String traceId,
        @Schema(description = "Saldo vencido do cliente") BigDecimal overdueBalance
) {
}
