package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Corpo do {@code PUT /pdv/comandas/{id}/store-purchase} (PDV-F036). */
@Data
public class StorePurchaseRequest {

    @NotNull
    @Schema(description = "O cliente da mesa comprou algo na loja?", example = "true")
    private Boolean boughtInStore;
}
