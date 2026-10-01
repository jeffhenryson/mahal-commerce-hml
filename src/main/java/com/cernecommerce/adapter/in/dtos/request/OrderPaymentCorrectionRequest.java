package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** Corpo do {@code POST /orders/{id}/payments/correction} (PDV-F030). */
@Data
public class OrderPaymentCorrectionRequest {

    @NotEmpty
    @Valid
    @Schema(description = "As formas certas. A soma tem que ser exatamente o totalPayable do pedido — "
            + "sem troco, nem em DINHEIRO.")
    private List<SalePaymentRequest> payments;

    @Size(max = 500)
    @Schema(description = "Por que a forma lançada estava errada. Obrigatório (400 REASON_REQUIRED).",
            example = "Cliente pagou no débito, operador marcou PIX")
    private String reason;
}
