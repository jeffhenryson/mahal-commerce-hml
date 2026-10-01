package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Corpo do {@code POST /receivables/{id}/cancel} — perdão ou erro de lançamento (CRM-F010). */
@Data
public class ReceivableCancelRequest {

    @NotBlank
    @Size(max = 500)
    @Schema(example = "Lançado no cliente errado")
    private String reason;
}
