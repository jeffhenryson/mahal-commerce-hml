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

    @Size(max = 50)
    @Schema(description = "PDV-F042 — SKU do sabor no catálogo. Sem ele e sem essencia, repete o sabor E o "
            + "SKU da origem; com essencia em texto e sem ele, a sessão nova fica só em texto.",
            example = "SENCE-MENTA")
    private String essenciaSku;

    @Schema(description = "Rosh duplo: cria também o 2º rosh a R$ 0, ligado, em NA_FILA.")
    private boolean duplo;

    @Size(max = 150)
    @Schema(description = "Sabor do 2º rosh. Obrigatório quando duplo=true.", example = "Zomo Uva")
    private String essenciaRosh;

    @Size(max = 50)
    @Schema(description = "PDV-F042 — SKU do sabor do 2º rosh no duplo.", example = "ZOMO-UVA")
    private String essenciaRoshSku;

    @Schema(description = "Faixa do 2º rosh; nula usa a da sessão.", example = "1")
    private Long tierIdRosh;

    @Schema(description = "PDV-F034 — paga no final, como em POST /sessoes. Não herda da origem; exige "
            + "PDV_SESSION_PAY_LATER.")
    private boolean pagarNoFinal;
}
