package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Item de kit pré-montado. {@code id} nulo cria; {@code id} existente atualiza. */
@Data
public class KitItemRequest {
    private Long id;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Min(1)
    private int quantity;

    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal unitCost;

    /** Categoria do catálogo (opcional). */
    private Long categoryId;

    /** Marca do catálogo (opcional). */
    private Long brandId;

    @Size(max = 500)
    private String description;

    /** URL devolvida por {@code POST /estoque/products/images}. */
    @Size(max = 2048)
    private String imageUrl;
}
