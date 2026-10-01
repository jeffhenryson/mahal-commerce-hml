package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Uma correção da forma de pagamento no {@code GET /orders/{id}/payment-history} (PDV-F027). */
public record PaymentCorrectionHistoryDTO(
        Long correctionId,
        @Schema(description = "Quando a correção foi feita.") Instant at,
        @Schema(description = "Username de quem corrigiu.") String by,
        String reason,
        @Schema(description = "Linhas aposentadas pela correção (hoje CORRECTED).") List<OrderPaymentResponseDTO> before,
        @Schema(description = "Linhas lançadas pela correção. Podem ter sido corrigidas de novo depois.")
        List<OrderPaymentResponseDTO> after) {
}
