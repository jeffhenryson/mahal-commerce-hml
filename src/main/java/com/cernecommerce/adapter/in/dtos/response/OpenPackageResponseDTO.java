package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/** Lata aberta rendendo sessões (EST-F027). */
@Data
@Schema(description = "Pacote em uso — a lata de essência já aberta no balcão")
public class OpenPackageResponseDTO {

    @Schema(description = "SKU da essência", example = "ESSE-ZGY-BLUEBERRY")
    private String sku;

    @Schema(description = "Nome do produto, para a tela não cruzar com o catálogo por linha",
            example = "Zgy Blueberry")
    private String productName;

    @Schema(description = "Depósito onde a lata está aberta", example = "LOJA-01")
    private String warehouseCode;

    @Schema(description = "Sessões já lançadas nesta lata", example = "3")
    private int uses;

    @Schema(description = "Quantas sessões esta lata rende — cópia do cadastro no momento da "
            + "abertura, não leitura viva: editar o produto não muda o tamanho de uma lata pela "
            + "metade.", example = "5")
    private int sessionsPerUnit;

    @Schema(description = "Sessões que ainda cabem nesta lata", example = "2")
    private int remaining;

    @Schema(description = "EST-F037 — a lata atingiu ou passou a estimativa de sessionsPerUnit (selo "
            + "\"acima da estimativa\"). Não é fim: ela só fecha com \"Acabou\" ou repondo.", example = "false")
    private boolean exhausted;

    @Schema(description = "Quando a lata foi aberta")
    private Instant openedAt;

    @Schema(description = "Quem abriu", example = "atendente")
    private String openedBy;

    @Schema(description = "EST-F037 — quando a lata foi fechada; nulo enquanto em uso")
    private Instant closedAt;

    @Schema(description = "EST-F037 — por que fechou: FINISHED (\"Acabou\"), REPLACED (reposta) ou EXHAUSTED "
            + "(fechamento automático, só em latas antigas); nulo enquanto em uso", example = "FINISHED")
    private String closeReason;
}
