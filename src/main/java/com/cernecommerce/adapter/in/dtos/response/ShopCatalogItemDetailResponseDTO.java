package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ShopCatalogItemDetailResponseDTO {
    private String sku;
    private String name;
    private String category;
    private BigDecimal price;
    private boolean available;
    private List<ShopCatalogVariantResponseDTO> variants;

    /** Estágio 01 do admin — link de imagem cadastrado manualmente pelo lojista. */
    private String imageUrl;

    /** Estágio 01 do admin — produto em promoção, usado pela vitrine "Promoções". */
    private boolean onSale;

    /** Preço "de" riscado na vitrine ("de/por"), quando cadastrado. */
    private BigDecimal originalPrice;

    /** Selo de destaque distinto de {@code onSale}. */
    private boolean superPromo;

    /** Descrição longa do produto, opcional. */
    private String description;

    /** Estágio 01 do admin — link de vídeo cadastrado manualmente. */
    private String videoUrl;

    /** Galeria de até 5 imagens ordenadas. Nunca nula; lista vazia se não cadastrada. */
    private List<String> images;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Promoções por quantidade vigentes no marketplace que "
            + "incluem o produto (ou uma variação): selo \"4 por R$ 10\" da vitrine. Vazio sem promoção.")
    private java.util.List<QuantityPromotionBadgeDTO> quantityPromotions = new java.util.ArrayList<>();
}
