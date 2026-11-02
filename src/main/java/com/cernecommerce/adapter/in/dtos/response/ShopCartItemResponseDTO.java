package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShopCartItemResponseDTO {
    private String sku;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private boolean available;
    // ECM-F008 — kit montável. Nulos/zero para linha avulsa.
    private String kitBundleId;
    private Long kitTemplateId;
    private Long kitStepId;
    private BigDecimal discountAmount;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Promoção por quantidade aplicada na linha. Nulo sem promoção.")
    private Long promotionId;

    private String promotionName;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Desconto de promoção por quantidade na linha (zero sem promoção).")
    private java.math.BigDecimal promotionDiscountAmount;
}
