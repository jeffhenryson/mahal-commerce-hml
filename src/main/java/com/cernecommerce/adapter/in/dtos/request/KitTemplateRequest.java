package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Corpo de criação/substituição de um kit montável (EST-F031). {@code PUT} substitui o modelo
 * inteiro: passo com {@code id} é atualizado no lugar, passo sem {@code id} é criado, e passo que
 * não veio é removido.
 */
@Data
public class KitTemplateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;

    @Size(max = 500)
    private String imageUrl;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax(value = "100.00", inclusive = false)
    private BigDecimal discountPercent;

    private Boolean active;
    private Boolean visibleInPos;
    private Boolean visibleInMarketplace;

    @NotEmpty
    @Valid
    private List<Step> steps;

    @Data
    @Schema(name = "KitTemplateStepRequest")
    public static class Step {
        private Long id;

        @NotBlank
        @Size(max = 100)
        private String name;

        @Min(0)
        private int displayOrder;

        @NotNull
        private Long categoryId;

        private boolean required;

        @Min(1)
        private int maxItems = 1;
    }
}
