package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class ShopCartResponseDTO {
    private List<ShopCartItemResponseDTO> items;
    /** Líquido: soma dos subtotais menos {@code discountTotal}. */
    private BigDecimal total;
    /** ECM-F008 — desconto dos kits montáveis; zero sem kit. */
    private BigDecimal discountTotal;
    private Instant updatedAt;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Desconto de promoção por quantidade do carrinho, "
            + "calculado no servidor. O total já sai líquido dele.")
    private java.math.BigDecimal promotionDiscountTotal;
}
