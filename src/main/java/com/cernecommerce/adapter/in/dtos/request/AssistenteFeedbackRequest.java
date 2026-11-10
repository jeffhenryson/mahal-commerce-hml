package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** {@code nota == null} limpa a avaliação. {@code motivo} é opcional (texto livre, sem dado pessoal). */
@Data
public class AssistenteFeedbackRequest {
    private NotaFeedback nota;

    @Size(max = 500, message = "O motivo deve ter até 500 caracteres.")
    private String motivo;
}
