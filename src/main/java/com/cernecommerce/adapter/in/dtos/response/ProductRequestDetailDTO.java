package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import com.cernecommerce.core.domain.model.estoque.ProductRequestUrgency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Pedido de cliente completo: os campos do {@link ProductRequestSummaryDTO} + solicitações e histórico. */
public record ProductRequestDetailDTO(
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
        int pendingNotifications,
        Instant createdAt,
        Instant updatedAt,
        long version,
        @Schema(nullable = true) String supplierNote,
        @Schema(nullable = true) String categoryOther,
        @Schema(description = "Nome de exibição de quem registrou") String createdBy,
        @Schema(nullable = true) Instant closedAt,
        @Schema(description = "Ativas primeiro, depois por criação") List<ProductRequestEntryDTO> entries,
        @Schema(description = "Mais recentes primeiro") List<ProductRequestEventDTO> events) {
}
