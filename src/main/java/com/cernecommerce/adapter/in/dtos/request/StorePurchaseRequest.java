package com.cernecommerce.adapter.in.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Corpo do {@code PUT /pdv/comandas/{id}/store-purchase} (PDV-F036). */
@Data
public class StorePurchaseRequest {

    @Schema(description = "O cliente da mesa comprou algo na loja? Opcional com orderId (vale true).",
            example = "true")
    private Boolean boughtInStore;

    @Positive
    @Schema(description = "A venda da loja feita pelo cliente da mesa — fica ligada à mesa e aparece no QR.",
            example = "1234")
    private Long orderId;

    @JsonIgnore
    @AssertTrue(message = "Informe boughtInStore ou orderId.")
    public boolean isRespondido() {
        return boughtInStore != null || orderId != null;
    }
}
