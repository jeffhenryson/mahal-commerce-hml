package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Item de uma venda de balcão.
 *
 * <p><b>{@code unitPrice} saiu deste request em PDV-F004.</b> O preço e o custo passam a ser
 * resolvidos pelo servidor via {@code EstoqueUseCase.findPricingBySku}. Aceitar preço do cliente
 * HTTP destruía a rastreabilidade: um valor menor era indistinguível de erro de digitação, de
 * desconto autorizado e de fraude.</p>
 */
@Data
public class SaleItemRequest {

    @NotBlank
    @Schema(description = "SKU do catálogo. O preço é resolvido pelo servidor.", example = "CARV-001")
    private String sku;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(description = "Quantidade vendida.", example = "2.000")
    private BigDecimal quantity;

    @DecimalMin(value = "0.0")
    @Schema(description = "Desconto em valor absoluto sobre este item. Exige a permissão "
            + "PDV_SALE_DISCOUNT e não pode passar do bruto do item.", example = "4.00")
    private BigDecimal discountAmount;

    @Size(max = 200)
    @Schema(description = "PDV-F022 — observação da linha, gravada no item do pedido.", example = "Sem gelo")
    private String note;

    @Min(0)
    @Schema(description = "Unidades desta linha presas a kit montável: não entram na promoção por quantidade.",
            example = "2")
    private Integer kitQuantity;

    @DecimalMin(value = "0.0")
    @Schema(description = "Desconto de promoção por quantidade que a tela mostrou nesta linha. Informativo: o "
            + "servidor recalcula e confere o total com `expectedPromotionDiscount`. Não exige PDV_SALE_DISCOUNT.")
    private BigDecimal promotionDiscountAmount;
}
