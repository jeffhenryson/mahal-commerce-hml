package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Novo pedido de cliente com a primeira solicitação. */
@Data
public class ProductRequestCreateRequest {

    @NotBlank
    @Size(min = 2, max = 160)
    @Schema(example = "Essência Ziggy Love 66 50g")
    private String description;

    @Size(max = 80)
    @Schema(nullable = true)
    private String brand;

    @Size(max = 80)
    @Schema(description = "Sabor, tamanho, cor...", nullable = true)
    private String variation;

    @Schema(description = "Exclusivo com categoryOther", nullable = true)
    private Long categoryId;

    @Size(max = 80)
    @Schema(description = "Quando o tipo não existe no catálogo", nullable = true)
    private String categoryOther;

    @NotNull
    @Valid
    private ProductRequestEntryRequest entry;

    @AssertTrue(message = "Informe a categoria do catálogo ou a outra, não as duas")
    @Schema(hidden = true)
    public boolean isCategoryExclusive() {
        return categoryId == null || categoryOther == null || categoryOther.isBlank();
    }
}
