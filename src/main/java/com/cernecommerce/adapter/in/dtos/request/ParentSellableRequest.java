package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Liga ou desliga a venda do SKU base de um produto com variações (EST-F036). Endpoint próprio, no
 * molde de {@link LotTrackedRequest}: muda o que venda e entrada de estoque aceitam daquele SKU.
 * {@code Boolean} com {@code @NotNull}: corpo sem o campo é 400, não um "desligar" silencioso.
 */
@Data
public class ParentSellableRequest {

    @NotNull
    @Schema(description = "true libera vender (e dar entrada) no SKU base.", example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean parentSellable;
}
