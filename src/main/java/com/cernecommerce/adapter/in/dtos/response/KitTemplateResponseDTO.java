package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class KitTemplateResponseDTO {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal discountPercent;
    private boolean active;
    private boolean visibleInPos;
    private boolean visibleInMarketplace;
    private List<Step> steps;

    @Data
    @Schema(name = "KitTemplateStepResponse")
    public static class Step {
        private Long id;
        private String name;
        private int displayOrder;
        private Long categoryId;
        private boolean required;
        private int maxItems;
    }
}
