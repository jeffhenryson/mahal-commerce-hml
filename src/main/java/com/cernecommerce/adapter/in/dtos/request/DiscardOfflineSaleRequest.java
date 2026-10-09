package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Descarte de uma venda offline recusada (PDV-F043): o dinheiro é acertado fora do sistema. */
@Data
public class DiscardOfflineSaleRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Por que a venda não vai entrar (ex.: cliente devolveu o produto).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
