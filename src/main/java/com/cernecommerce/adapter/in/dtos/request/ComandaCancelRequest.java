package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Corpo opcional do {@code POST /pdv/comandas/{id}/cancel} (PDV-F029). */
@Data
public class ComandaCancelRequest {

    @Size(max = 500)
    @Schema(description = "Por que a mesa foi cancelada. Fica no histórico.", example = "Cliente desistiu")
    private String reason;
}
