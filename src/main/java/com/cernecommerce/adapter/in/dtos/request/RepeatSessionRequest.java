package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** "Repetir sessão" (PDV-F027): mesma configuração da sessão de origem, sabor opcionalmente novo. */
@Data
public class RepeatSessionRequest {

    @Size(max = 150)
    @Schema(description = "Sabor da nova sessão; nulo ou vazio repete o da origem.", example = "Sence Menta")
    private String essencia;

    @Schema(description = "Rosh duplo: cria também o 2º rosh a R$ 0, ligado, em NA_FILA.")
    private boolean duplo;

    @Size(max = 150)
    @Schema(description = "Sabor do 2º rosh. Obrigatório quando duplo=true.", example = "Zomo Uva")
    private String essenciaRosh;

    @Schema(description = "Faixa do 2º rosh; nula usa a da sessão.", example = "1")
    private Long tierIdRosh;

    @Schema(description = "PDV-F034 — paga no final, como em POST /sessoes. Não herda da origem; exige "
            + "PDV_SESSION_PAY_LATER.")
    private boolean pagarNoFinal;
}
