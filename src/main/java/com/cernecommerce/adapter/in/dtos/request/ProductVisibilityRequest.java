package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Liga ou desliga produtos no app em lote (ECM-F013, Central de Pedidos). */
@Data
public class ProductVisibilityRequest {

    @NotEmpty
    @Size(max = 500)
    private List<@NotBlank @Size(max = 50) String> skus;

    @NotNull
    @Schema(description = "true mostra no app (frontend-market); false esconde do catálogo e do detalhe.")
    private Boolean visibleInMarketplace;
}
