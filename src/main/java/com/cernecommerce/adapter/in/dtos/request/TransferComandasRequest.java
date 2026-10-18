package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Passagem de turno (PDV-F054): as mesas abertas de um caixa passam para outro. */
@Data
public class TransferComandasRequest {

    @NotNull
    @Schema(description = "Caixa de quem sai — de onde as mesas saem.", example = "12")
    private Long fromSessionId;

    @NotNull
    @Schema(description = "Caixa aberto de quem assume — para onde as mesas vão.", example = "15")
    private Long toSessionId;

    @Size(max = 100)
    @Schema(description = "Mesas a transferir. Vazio ou ausente: todas as abertas do caixa de origem.")
    private List<@NotNull Long> comandaIds;
}
