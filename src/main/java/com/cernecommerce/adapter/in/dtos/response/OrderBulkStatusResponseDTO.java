package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Resultado do {@code POST /orders/bulk-status}: cada pedido falha sozinho, sem desfazer os demais.
 */
public record OrderBulkStatusResponseDTO(
        @Schema(description = "Pedidos movidos com sucesso.") List<Long> ok,
        @Schema(description = "Pedidos recusados, com o mesmo errorCode que o endpoint unitário daria.")
        List<Failure> failed) {

    public record Failure(Long orderId, String code, String message) {
    }
}
