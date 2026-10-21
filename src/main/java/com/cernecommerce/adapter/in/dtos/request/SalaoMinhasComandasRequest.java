package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** CPF ou celular com a confirmação, como no {@link SalaoEntrarRequest}. */
public record SalaoMinhasComandasRequest(
        @Size(max = 20) String cpf,
        @Size(max = 20) String celular,
        @NotBlank @Pattern(regexp = "\\d{3,4}", message = "Confirme com 3 ou 4 dígitos.") String confirmacao) {
}
