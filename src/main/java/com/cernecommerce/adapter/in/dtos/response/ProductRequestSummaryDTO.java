package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import com.cernecommerce.core.domain.model.estoque.ProductRequestUrgency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Pedido de cliente na listagem. Agregados só das solicitações ACTIVE. */
public record ProductRequestSummaryDTO(
        long id,
        String description,
        @Schema(nullable = true) String brand,
        @Schema(nullable = true) String variation,
        @Schema(nullable = true) Long categoryId,
        @Schema(description = "Nome da categoria ou o categoryOther", nullable = true) String categoryName,
        ProductRequestStatus status,
        @Schema(nullable = true) String linkedSku,
        @Schema(nullable = true) String linkedProductName,
        @Schema(nullable = true) LocalDate expectedAt,
        int entriesCount,
        int totalQuantity,
        @Schema(nullable = true) ProductRequestUrgency maxUrgency,
        @Schema(nullable = true) LocalDate earliestNeededBy,
        @Schema(nullable = true) BigDecimal maxPriceMin,
        @Schema(nullable = true) BigDecimal maxPriceMax,
        @Schema(description = "Solicitações ativas ainda sem aviso") int pendingNotifications,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
