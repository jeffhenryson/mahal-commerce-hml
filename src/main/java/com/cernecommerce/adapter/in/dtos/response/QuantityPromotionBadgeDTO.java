package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Selo de promoção por quantidade na vitrine da loja. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Promoção por quantidade vigente no marketplace que inclui o produto")
public class QuantityPromotionBadgeDTO {
    private Long id;
    private Integer quantity;
    private BigDecimal bundlePrice;
    @Schema(example = "4 por R$ 10")
    private String label;
}
