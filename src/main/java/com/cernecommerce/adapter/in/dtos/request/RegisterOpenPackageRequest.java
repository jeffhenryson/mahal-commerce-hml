package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cadastro de uma lata que já estava aberta antes do sistema (EST-F033). */
@Data
public class RegisterOpenPackageRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    @Schema(description = "Depósito da lata", example = "LOJA-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String warehouseCode;

    // Só o piso fica aqui: o teto é o sessionsPerUnit do produto, que o service valida.
    @NotNull
    @Min(1)
    @Schema(description = "Sessões que a lata ainda rende — entre 1 e o sessionsPerUnit do produto",
            example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer usesRemaining;
}
