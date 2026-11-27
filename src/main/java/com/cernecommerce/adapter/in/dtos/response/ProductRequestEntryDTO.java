package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.estoque.ProductRequestEntryStatus;
import com.cernecommerce.core.domain.model.estoque.ProductRequestNotifyChannel;
import com.cernecommerce.core.domain.model.estoque.ProductRequestUrgency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Solicitação de um cliente. */
public record ProductRequestEntryDTO(
        long id,
        @Schema(nullable = true) Long customerId,
        @Schema(description = "Do CRM (anonimizado vira \"Cliente excluído\") ou o do avulso") String customerName,
        @Schema(description = "Mascarado \"(85) 9****-1234\" sem CRM_CUSTOMER_READ", nullable = true)
        String customerPhone,
        @Schema(description = "true = cliente do CRM") boolean registered,
        int quantity,
        @Schema(nullable = true) BigDecimal maxPrice,
        ProductRequestUrgency urgency,
        @Schema(nullable = true) LocalDate neededBy,
        @Schema(nullable = true) String note,
        ProductRequestEntryStatus status,
        @Schema(nullable = true) Instant notifiedAt,
        @Schema(nullable = true) ProductRequestNotifyChannel notifiedChannel,
        @Schema(description = "Nome de exibição de quem registrou") String createdBy,
        Instant createdAt) {
}
