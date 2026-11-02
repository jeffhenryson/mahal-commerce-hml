package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** V183 — segmento manual do cliente. */
@Data
public class CustomerSegmentoRequest {

    @Schema(description = "Novo, Recorrente, VIP, Em Risco ou Inativo — congela o segmento (o recálculo diário não "
            + "sobrescreve). Nulo devolve o cliente ao cálculo automático.", example = "VIP", nullable = true)
    private String segmento;
}
