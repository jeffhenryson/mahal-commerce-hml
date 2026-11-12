package com.cernecommerce.adapter.in.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.math.BigDecimal;

@Data
public class ReorderPointRequest {
    @NotBlank
    private String warehouseCode;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal minQuantity;

    /**
     * Corte do nível crítico ({@code 0 ≤ c < minQuantity}). Ausente: mantém o crítico salvo se
     * ainda for menor que o mínimo, senão volta ao padrão. {@code null} explícito: volta ao padrão
     * (metade do mínimo).
     */
    @Schema(nullable = true, description = "Ausente mantém o crítico salvo (se ainda < minQuantity); "
            + "null explícito volta ao padrão de 50%. Deve ficar em 0 ≤ criticalQuantity < minQuantity.")
    @Setter(AccessLevel.NONE)
    private BigDecimal criticalQuantity;

    /** {@code true} quando o JSON trouxe a chave {@code criticalQuantity}, mesmo com valor null. */
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean criticalQuantityPresent;

    public void setCriticalQuantity(BigDecimal criticalQuantity) {
        this.criticalQuantity = criticalQuantity;
        this.criticalQuantityPresent = true;
    }
}
