package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** PDV-F045 — abrir à mão, na Central de cigarros, 1 carteira em maços ou 1 maço em soltos. */
@Data
public class AbrirEmbalagemRequest {

    @NotBlank
    @Size(max = 50)
    @Schema(description = "SKU da embalagem fechada a abrir (a carteira, ou o maço).", example = "LM-AZUL-CART")
    private String sku;

    // Inteiro: não existe meia carteira. O teto é só sanidade de balcão.
    @NotNull
    @Min(1)
    @Max(100)
    @Schema(description = "Quantas embalagens abrir.", example = "1")
    private Integer quantidade;

    @Size(max = 50)
    @Schema(description = "Opcional. O depósito é o do caixa aberto do operador; outro valor dá 400 WAREHOUSE_MISMATCH.",
            example = "PRINCIPAL")
    private String warehouseCode;
}
