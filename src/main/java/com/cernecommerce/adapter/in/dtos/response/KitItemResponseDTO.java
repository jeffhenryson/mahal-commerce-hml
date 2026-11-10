package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

/** Item de kit pré-montado (admin), com custo. Ordenado por posição. */
@Data
public class KitItemResponseDTO {
    private Long id;
    private String name;
    private int quantity;
    private BigDecimal unitCost;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private String description;
    private String imageUrl;
}
