package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pedido.RefundDestination;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** PED-F004 — unidades de um SKU a estornar e o destino da mercadoria. */
@Data
public class RefundItemRequest {

    @NotBlank
    @Schema(description = "SKU vendido no pedido. Com duas linhas do mesmo SKU, as unidades são tiradas "
            + "na ordem da venda.", example = "SEDA-001")
    private String sku;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 11, fraction = 3)
    @Schema(description = "Até o vendido menos o já estornado (refundedQuantity); acima disso, 422 "
            + "REFUND_QUANTITY_EXCEEDED.", example = "1")
    private BigDecimal quantity;

    @NotNull
    @Schema(description = "RESTOCK volta ao estoque; DISCARD é perda/avaria (o saldo não muda); "
            + "CUSTOMER_KEEPS devolve só o dinheiro. Pedido de mesa nunca volta ao estoque: RESTOCK vira "
            + "CUSTOMER_KEEPS.", example = "DISCARD")
    private RefundDestination destination;
}
