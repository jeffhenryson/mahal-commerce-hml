package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pedido.RefundDestination;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** PED-F006 — troca de unidades de um item do pedido por outro produto. */
@Data
public class OrderItemExchangeRequest {

    @NotBlank
    @Schema(description = "SKU vendido no pedido que o cliente devolve.", example = "ZOMO-50-UVA")
    private String sku;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 11, fraction = 3)
    @Schema(description = "Quantas unidades voltam: até o vendido menos o já estornado.", example = "1")
    private BigDecimal quantity;

    @NotNull
    @Schema(description = "RESTOCK volta ao estoque do depósito do pedido; DISCARD é perda/avaria (o saldo não "
            + "muda, o custo fica registrado). CUSTOMER_KEEPS não vale aqui.", example = "RESTOCK")
    private RefundDestination destination;

    @NotBlank
    @Schema(description = "SKU vendável que sai no lugar (variação, ou base com parentSellable).",
            example = "ZOMO-50-MENTA")
    private String newSku;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 11, fraction = 3)
    @Schema(example = "1")
    private BigDecimal newQuantity;

    @NotBlank
    @Size(min = 3, max = 255)
    @Schema(example = "sabor errado")
    private String reason;

    @Pattern(regexp = "PIX|DINHEIRO|DEBITO|CREDITO")
    @Schema(description = "Forma da diferença. Obrigatória só quando há diferença (400 SETTLEMENT_METHOD_REQUIRED).",
            example = "PIX")
    private String settlementMethod;
}
