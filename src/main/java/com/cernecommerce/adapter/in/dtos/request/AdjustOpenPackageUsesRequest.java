package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Correção dos usos da lata aberta pelo gerente (PATCH /estoque/open-packages/{sku}/uses).
 *
 * <p>{@code uses} sem {@code @Min}/{@code @Max} de propósito: a faixa é conferida no domínio, para
 * sair como {@code OPEN_PACKAGE_INVALID_USES}, que é o que o front trata, e não como o erro genérico
 * de validação.</p>
 */
@Data
public class AdjustOpenPackageUsesRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    @Schema(description = "Depósito da lata", example = "SALAO", requiredMode = Schema.RequiredMode.REQUIRED)
    private String warehouseCode;

    @NotNull
    @Schema(description = "Quantas sessões a lata já fez, de 0 a 999", example = "3",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer uses;

    @Size(max = 255)
    @Schema(description = "Motivo da correção, para a auditoria", example = "Correção no PDV", nullable = true)
    private String reason;
}
