package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** PDV-F046 — o teto do limite de "Marcar" que quem chama pode dar. */
@Schema(description = "PDV-F046 — teto do limite de crédito que o usuário pode definir")
public record CreditLimitCapResponseDTO(
        @Schema(description = "Teto por canal e para o total. null = sem teto (gerente, com RECEIVABLE_MANAGE).",
                example = "100.00")
        BigDecimal maxCreditLimit) {
}
