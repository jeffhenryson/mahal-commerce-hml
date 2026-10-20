package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mesa com QR do autoatendimento. {@code ativo} só vale no PUT; mesa inativa invalida o QR. */
public record SalaoMesaRequest(
        @NotBlank @Size(max = 60) @Schema(example = "Mesa 4") String nome,
        @Schema(defaultValue = "true") Boolean ativo) {
}
