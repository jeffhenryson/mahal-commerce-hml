package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** SKU informado manualmente para uma linha que o casamento automático por EAN não resolveu. */
@Data
public class NfeLineOverrideRequest {

    @Min(1)
    @Schema(description = "nItem da linha na NF-e (o mesmo devolvido no preview).", example = "3")
    private int itemNumber;

    @jakarta.validation.constraints.Size(max = 50)
    @Schema(description = "SKU do catálogo a associar a esta linha. Obrigatório para linha UNMATCHED; "
            + "omitido mantém o casamento do preview (útil quando o override só traz o fator).",
            example = "ESS-MENTA-50", nullable = true)
    private String sku;

    @Min(1)
    @Schema(description = "V182 — unidades de estoque em uma unidade comercial da nota (uCom). Ex.: a nota "
            + "diz 2 CX e cada caixa tem 12: mande 12, e entram 24 unidades com custo vUnCom/12. Omitido usa "
            + "o fator do preview (lembrado do fornecedor) ou 1. Lembrado por fornecedor + cProd.",
            example = "12", nullable = true)
    private Integer unitsPerPackage;
}
