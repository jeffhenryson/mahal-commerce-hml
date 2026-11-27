package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Item aberto parecido com o que está sendo registrado. */
public record ProductRequestSimilarDTO(
        long id,
        String description,
        @Schema(nullable = true) String brand,
        @Schema(nullable = true) String variation,
        ProductRequestStatus status,
        int entriesCount,
        @Schema(description = "0 a 1 (trigramas, como o pg_trgm)") double similarity) {
}
