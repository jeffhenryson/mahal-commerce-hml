package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.Size;

/** CPF ou celular do lead, com ou sem máscara. Um dos dois. */
public record SalaoIdentificacaoRequest(@Size(max = 20) String cpf, @Size(max = 20) String celular) {
}
