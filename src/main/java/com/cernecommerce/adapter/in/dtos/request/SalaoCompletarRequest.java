package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Completa o cadastro de quem se identificou pelo {@code cpf} ou {@code celular}: os campos
 * {@code novo*} e {@code dataNascimento} só preenchem o que estiver vazio.
 */
public record SalaoCompletarRequest(
        @Size(max = 20) String cpf,
        @Size(max = 20) String celular,
        @Size(max = 20) String novoCpf,
        @Size(max = 20) String novoCelular,
        LocalDate dataNascimento) {
}
