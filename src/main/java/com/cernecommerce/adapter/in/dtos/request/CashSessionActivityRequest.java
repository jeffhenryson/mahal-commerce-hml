package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pdv.CashSessionActivityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** PDV-F047 — atividade do operador na sessão de caixa. */
@Data
public class CashSessionActivityRequest {

    @NotNull
    @Schema(description = "O que foi feito. OUTRO exige note.", example = "TROCAR_DINHEIRO")
    private CashSessionActivityType type;

    @Size(max = 280)
    @Schema(description = "Observação livre; obrigatória quando type = OUTRO.",
            example = "Troquei 2 notas de 100 no mercado")
    private String note;
}
