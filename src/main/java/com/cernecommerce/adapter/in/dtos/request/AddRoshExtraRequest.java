package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 2º rosh de uma sessão (PDV-F021): nova essência, mesmos utensílios. */
@Data
public class AddRoshExtraRequest {

    @Schema(description = "Faixa da nova essência; nula usa a faixa da sessão.", example = "1")
    private Long tierId;

    // Sem @NotBlank desde PDV-F042: texto OU essenciaSku, conferido no controller.
    @Size(max = 150)
    @Schema(description = "Essência do rosh. Obrigatória se não vier essenciaSku.", example = "Sence Menta")
    private String essencia;

    @Size(max = 50)
    @Schema(description = "PDV-F042 — SKU do sabor no catálogo; consome a lata do sabor.", example = "SENCE-MENTA")
    private String essenciaSku;
}
