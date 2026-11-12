package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Novo título da conversa (espaços repetidos viram um só). */
@Data
public class AssistenteRenomearRequest {
    @NotBlank(message = "Informe um título.")
    @Size(max = 80, message = "O título deve ter até 80 caracteres.")
    private String titulo;
}
