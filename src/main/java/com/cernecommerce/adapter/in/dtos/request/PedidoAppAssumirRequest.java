package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Outro caixa assume um pedido do app já aceito (ECM-F010). */
@Data
public class PedidoAppAssumirRequest {

    @NotNull
    @Schema(description = "O caixa aberto do próprio operador.", example = "43")
    private Long sessionId;
}
