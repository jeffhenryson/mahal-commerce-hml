package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Cadastro do lead pela página do QR: dados completos e o aceite de uso dos dados (LGPD). */
public record SalaoCadastroRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 20) String celular,
        @NotBlank @Size(max = 20) String cpf,
        @NotNull LocalDate dataNascimento,
        @AssertTrue(message = "É preciso aceitar o uso dos dados para o cadastro.") boolean aceite) {
}
