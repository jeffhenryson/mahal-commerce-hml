package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Recusa de um pedido do app pela loja (ECM-F010). */
@Data
public class PedidoAppRecusaRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Motivo, enviado ao cliente por e-mail e mostrado no app.",
            example = "Essência Menta acabou")
    private String motivo;
}
