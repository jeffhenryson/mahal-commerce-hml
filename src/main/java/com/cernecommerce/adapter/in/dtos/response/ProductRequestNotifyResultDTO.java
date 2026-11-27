package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado do aviso aos clientes. */
public record ProductRequestNotifyResultDTO(
        @Schema(description = "Solicitações marcadas como avisadas") int notified,
        @Schema(description = "Cliente avulso, excluído, sem contato ou fora do segmento das automações")
        int skippedNoConsent,
        @Schema(description = "true se não há automação ativa de PEDIDO_PRODUTO_CHEGOU (nada foi marcado)")
        boolean skippedNoAutomation,
        ProductRequestDetailDTO request) {
}
