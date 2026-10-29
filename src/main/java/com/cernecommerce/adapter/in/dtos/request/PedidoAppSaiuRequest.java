package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Saída para entrega de um pedido do app (ECM-F013). Corpo opcional. */
@Data
public class PedidoAppSaiuRequest {

    @Size(max = 500)
    @Schema(description = "Link para acompanhar a corrida (Uber, 99…), só http(s). Vai para delivery.trackingUrl e "
            + "para o aviso ao cliente.", example = "https://99app.com/tracking/abc123")
    private String linkViagem;
}
