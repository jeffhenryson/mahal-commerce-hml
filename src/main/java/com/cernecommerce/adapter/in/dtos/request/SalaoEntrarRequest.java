package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrar na própria mesa pelo CPF ou celular. {@code confirmacao}: os 4 últimos dígitos do celular
 * cadastrado (quem veio pelo CPF) ou os 3 primeiros do CPF (quem veio pelo celular).
 */
public record SalaoEntrarRequest(
        @Size(max = 20) String cpf,
        @Size(max = 20) String celular,
        @NotBlank @Pattern(regexp = "\\d{3,4}", message = "Confirme com 3 ou 4 dígitos.") String confirmacao,
        @NotBlank @Size(max = 64) String dispositivo) {
}
