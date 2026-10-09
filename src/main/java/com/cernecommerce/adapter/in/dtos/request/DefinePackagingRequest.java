package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Ligar um SKU à embalagem que o contém (EST-F032). */
@Data
public class DefinePackagingRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Schema(description = "SKU da embalagem de fora (ex.: o maço, para a unidade).", example = "LM-AZUL-MACO",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String parentSku;

    // Fator 1 não é embalagem — é o mesmo item com outro nome.
    @NotNull
    @Min(2)
    @Schema(description = "Quantos deste SKU uma embalagem de fora contém.", example = "20",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer unitsPerParent;
}
