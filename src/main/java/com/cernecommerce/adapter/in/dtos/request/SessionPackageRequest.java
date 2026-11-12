package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** PDV-F061 — um pacote da loja escolhido na Central de essências para a sessão. */
@Data
public class SessionPackageRequest {

    @NotBlank
    @Size(max = 50)
    @Schema(description = "SKU vendável da essência (a variação).", example = "ZOMO-UVA")
    private String sku;

    @Min(1)
    @Schema(description = "Quantidade de pacotes; SKU repetido na lista soma.", example = "2")
    private int quantidade = 1;
}
