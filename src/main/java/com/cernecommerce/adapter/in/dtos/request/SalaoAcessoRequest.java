package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** O código de 4 dígitos da mesa ocupada, digitado por outro aparelho. */
public record SalaoAcessoRequest(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "O código tem 4 dígitos.") String codigo,
        @NotBlank @Size(max = 64) String dispositivo) {
}
