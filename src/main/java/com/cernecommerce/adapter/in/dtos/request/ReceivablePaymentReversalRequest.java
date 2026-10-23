package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Corpo do {@code POST /receivables/payment-batches/{batchId}/reverse} — quitação lançada por engano (V173). */
@Data
public class ReceivablePaymentReversalRequest {

    @NotBlank
    @Size(max = 500)
    @Schema(example = "Caixa 62 — DINHEIRO lançado por engano, a cliente não pagou")
    private String reason;
}
