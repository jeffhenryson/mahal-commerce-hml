package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * PDV-F060 — compra na loja lançada na conta da mesa: os mesmos itens do {@link SaleRequest} do caixa,
 * sem pagamento — eles são cobrados junto com a sessão paga no final.
 */
@Data
public class StorePurchaseOnTabRequest {

    @Schema(description = "Cliente da compra. Aceito pela simetria com o SaleRequest; a mesa mantém o "
            + "cliente dela.", example = "5")
    private Long customerId;

    @NotEmpty
    @Valid
    private List<SaleItemRequest> items;
}
