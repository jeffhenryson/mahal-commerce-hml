package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Cotação do kit montado: preço cheio, desconto do kit e total, e a parte de cada linha. */
@Data
public class KitQuoteResponseDTO {
    private Long templateId;
    private String templateName;
    private BigDecimal discountPercent;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;
    private List<Line> lines;

    @Data
    @Schema(name = "KitQuoteLine")
    public static class Line {
        private Long stepId;
        private String stepName;
        private String sku;
        private String productName;
        private BigDecimal unitPrice;
        private BigDecimal discountAmount;
        private BigDecimal netAmount;
    }
}
