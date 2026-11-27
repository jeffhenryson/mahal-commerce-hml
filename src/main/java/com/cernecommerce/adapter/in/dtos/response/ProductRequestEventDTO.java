package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/** Linha do histórico do pedido de cliente. */
public record ProductRequestEventDTO(
        long id,
        @Schema(description = "CREATED, ENTRY_ADDED, ENTRY_UPDATED, ENTRY_CANCELLED, STATUS_CHANGED, UPDATED, "
                + "LINKED_SKU, NOTIFIED") String type,
        @Schema(nullable = true) ProductRequestStatus fromStatus,
        @Schema(nullable = true) ProductRequestStatus toStatus,
        @Schema(nullable = true) Long entryId,
        @Schema(description = "Ex.: { sku }, { reason }, { channel, count }", nullable = true)
        Map<String, Object> payload,
        @Schema(description = "Nome de exibição do usuário, ou \"sistema\"") String actor,
        Instant at) {
}
