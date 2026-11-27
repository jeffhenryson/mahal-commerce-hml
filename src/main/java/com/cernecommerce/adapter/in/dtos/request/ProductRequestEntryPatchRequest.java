package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.estoque.ProductRequestUrgency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Edição de uma solicitação. Ausente/{@code null} = não muda; {@code note: ""} limpa a nota. */
@Data
public class ProductRequestEntryPatchRequest {

    @Min(1)
    @Schema(nullable = true)
    private Integer quantity;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Schema(nullable = true)
    private BigDecimal maxPrice;

    @Schema(nullable = true)
    private ProductRequestUrgency urgency;

    @Schema(nullable = true)
    private LocalDate neededBy;

    @Size(max = 500)
    @Schema(nullable = true)
    private String note;
}
