package com.cernecommerce.infra.handler;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** 409 PROMOTION_CHANGED: o corpo padrão mais o desconto de promoção que o servidor calculou agora. */
@Schema(description = "A promoção por quantidade mudou entre a tela e o servidor")
public record PromotionChangedError(
        String message,
        @Schema(description = "Sempre PROMOTION_CHANGED") String errorCode,
        Instant timestamp,
        String path,
        String traceId,
        @Schema(description = "Desconto de promoção que o servidor aplicaria agora") BigDecimal currentPromotionDiscount
) {
}
