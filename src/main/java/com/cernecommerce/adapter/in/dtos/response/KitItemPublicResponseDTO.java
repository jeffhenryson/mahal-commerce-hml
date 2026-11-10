package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

/** Item de kit pré-montado para o app e o PDV: o que vem no kit, sem custo. */
@Data
public class KitItemPublicResponseDTO {
    private String name;
    private int quantity;
    private String categoryName;
    private String brandName;
    private String description;
    private String imageUrl;
}
