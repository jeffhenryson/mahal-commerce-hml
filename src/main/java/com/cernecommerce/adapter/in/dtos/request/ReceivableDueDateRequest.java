package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** Corpo do {@code PATCH /receivables/{id}/due-date} — renegociar o prazo (CRM-F010). */
@Data
public class ReceivableDueDateRequest {

    @NotNull
    @Schema(description = "Novo vencimento, hoje ou depois.", example = "2026-11-15")
    private LocalDate dueDate;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
