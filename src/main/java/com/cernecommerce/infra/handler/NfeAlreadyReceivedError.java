package com.cernecommerce.infra.handler;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 409 NFE_ALREADY_RECEIVED: o corpo padrão mais o recebimento que já contém a nota. */
@Schema(description = "A NF-e com esta chave de acesso já foi recebida")
public record NfeAlreadyReceivedError(
        String message,
        @Schema(description = "Sempre NFE_ALREADY_RECEIVED") String errorCode,
        Instant timestamp,
        String path,
        String traceId,
        @Schema(description = "Chave de acesso (44 dígitos)") String accessKey,
        @Schema(description = "Recebimento que já contém esta nota") Long goodsReceiptId,
        @Schema(description = "Quando a nota foi recebida") Instant receivedAt
) {
}
