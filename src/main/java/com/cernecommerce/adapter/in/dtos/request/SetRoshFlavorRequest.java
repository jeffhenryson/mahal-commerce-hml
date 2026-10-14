package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** PDV-F044 — o sabor do 2º rosh do duplo, escolhido quando a 1ª sessão acaba. */
@Data
public class SetRoshFlavorRequest {

    // Texto OU essenciaSku, conferido no controller — mesmo critério de AddRoshExtraRequest.
    @Size(max = 150)
    @Schema(description = "Essência do 2º rosh. Obrigatória se não vier essenciaSku; vazia com SKU usa o nome do produto.",
            example = "Zomo Menta")
    private String essencia;

    @Size(max = 50)
    @Schema(description = "SKU do sabor (variação) no catálogo; consome um uso da lata aberta do sabor.",
            example = "ZOMO-MENTA")
    private String essenciaSku;

    @Size(max = 50)
    @Schema(description = "PDV-F051 — 2º sabor da sessão mesclada (exige o 1º e tem de ser outro: 400 "
            + "SAME_FLAVOR_MIX). Consome 1 uso da lata de cada sabor; a faixa é a de maior preço entre os dois.",
            example = "ZIGY-MENTA")
    private String essenciaSku2;

    @Schema(description = "Faixa do 2º rosh, ignorada se a essência tiver faixa na ficha (PDV-F050); nula "
            + "usa a da sessão. O preço continua R$ 0.", example = "2")
    private Long tierId;
}
