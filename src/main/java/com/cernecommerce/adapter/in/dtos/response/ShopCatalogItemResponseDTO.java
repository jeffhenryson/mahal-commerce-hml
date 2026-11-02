package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Item resumido do catálogo público (ECM-F002). Deliberadamente sem custo/markup/margem — esses
 * campos moram em {@link PricingResponseDTO}, exclusiva do admin ({@code ProductResponseDTO}).
 */
@Data
public class ShopCatalogItemResponseDTO {
    private String sku;
    private String name;
    private String category;
    private BigDecimal price;
    private boolean available;

    /** Estágio 01 do admin — link de imagem cadastrado manualmente pelo lojista. */
    private String imageUrl;

    /** Estágio 01 do admin — produto em promoção, usado pela vitrine "Promoções". */
    private boolean onSale;

    /** Preço "de" riscado na vitrine ("de/por"), quando cadastrado. */
    private BigDecimal originalPrice;

    /** Selo de destaque distinto de {@code onSale}. */
    private boolean superPromo;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Promoções por quantidade vigentes no marketplace que "
            + "incluem o produto (ou uma variação): selo \"4 por R$ 10\" da vitrine. Vazio sem promoção.")
    private java.util.List<QuantityPromotionBadgeDTO> quantityPromotions = new java.util.ArrayList<>();
}
