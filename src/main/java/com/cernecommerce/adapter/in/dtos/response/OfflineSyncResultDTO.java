package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** O que aconteceu com uma venda do lote offline (PDV-F043). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineSyncResultDTO {

    private String clientSaleId;

    @Schema(description = "SYNCED (registrada agora), DUPLICATE (já estava registrada) ou REJECTED (foi "
            + "para revisão — o caixa não fecha até ela ser resolvida).")
    private String status;

    @Schema(description = "Pedido, em SYNCED e DUPLICATE.")
    private Long orderId;

    @Schema(description = "A recusa guardada, em REJECTED.")
    private Long rejectionId;

    @Schema(description = "Mesmo código que a venda online responderia (ex.: INSUFFICIENT_STOCK).")
    private String errorCode;

    private String message;
}
