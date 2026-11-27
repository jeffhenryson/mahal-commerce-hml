package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Edição do pedido de cliente. Ausente/{@code null} = não muda. Nos textos opcionais (marca, variação, outra
 * categoria, nota do fornecedor) e em {@code expectedAt}, {@code ""} limpa. Informar {@code categoryId} limpa
 * {@code categoryOther} e vice-versa.
 */
@Data
public class ProductRequestPatchRequest {

    @Size(min = 2, max = 160)
    @Schema(nullable = true)
    private String description;

    @Size(max = 80)
    @Schema(nullable = true)
    private String brand;

    @Size(max = 80)
    @Schema(nullable = true)
    private String variation;

    @Schema(nullable = true)
    private Long categoryId;

    @Size(max = 80)
    @Schema(nullable = true)
    private String categoryOther;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "expectedAt deve ser YYYY-MM-DD ou vazio")
    @Schema(description = "YYYY-MM-DD; \"\" limpa", nullable = true)
    private String expectedAt;

    @Size(max = 500)
    @Schema(nullable = true)
    private String supplierNote;

    @AssertTrue(message = "Informe a categoria do catálogo ou a outra, não as duas")
    @Schema(hidden = true)
    public boolean isCategoryExclusive() {
        return categoryId == null || categoryOther == null || categoryOther.isBlank();
    }
}
