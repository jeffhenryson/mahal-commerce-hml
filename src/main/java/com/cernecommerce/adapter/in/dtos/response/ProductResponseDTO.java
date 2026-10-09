package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductResponseDTO {
    private Long id;
    private String sku;
    private String name;
    /** Nome da categoria. Mantido como texto por compatibilidade — ver {@code categoryId}. */
    private String category;

    /**
     * Id da categoria vinculada, ou nulo em produto ainda não vinculado. O nome acima é o desta
     * categoria, denormalizado e mantido em sincronia pelo backend.
     */
    private Long categoryId;
    private String brand;

    /**
     * Id da marca vinculada, ou nulo em produto ainda não vinculado. O nome acima é o desta
     * marca, denormalizado e mantido em sincronia pelo backend.
     */
    private Long brandId;

    /** Estágio 01 do admin — link de imagem cadastrado manualmente pelo lojista. */
    private String imageUrl;

    /** Estágio 01 do admin — produto em promoção. */
    private boolean onSale;

    /** Selo de destaque distinto de {@code onSale}. */
    private boolean superPromo;

    /** Descrição longa do produto, opcional. */
    private String description;

    /** Estágio 01 do admin — link de vídeo cadastrado manualmente. */
    private String videoUrl;

    /** Galeria de até 5 imagens ordenadas. Nunca nula; lista vazia se não cadastrada. */
    private List<String> images;

    /**
     * Atributos descritivos do próprio produto. Nunca nulo; lista vazia se não cadastrados.
     * Distintos dos que vêm dentro de cada item de {@code variants}.
     */
    private List<ProductAttributeResponseDTO> attributes;

    private boolean active;
    private List<ProductVariantResponseDTO> variants;

    /** EST-F019 — nunca nulo; produto sem preço vem com os campos internos nulos. */
    private PricingResponseDTO pricing;

    /** {@code SIMPLES} ou {@code KIT} (EST-F015). */
    private String type;

    /** EST-F008 — opt-in: ENTRADA deste SKU passa a exigir lote e validade. */
    private boolean lotTracked;

    /**
     * EST-F036 — a base deste produto é vendável? Com variações e {@code false}, o SKU base não se
     * vende nem recebe entrada de estoque, e o PDV deve listar só as variações. Sem variações, a base é
     * sempre vendável e o campo não se aplica.
     */
    private boolean parentSellable;

    /** Código de barras/EAN, quando cadastrado. */
    private String barcode;

    /** Unidade de medida. {@code UN} por padrão. */
    private String unit;

    /** Testador/amostra, distinto de produto padrão da Mahal. */
    private boolean sampleProduct;

    /** Elegível a entrar como componente de kit. */
    private boolean kitComponentEligible;

    /** Aparece no PDV. */
    private boolean visibleInPos;

    /** Aparece no marketplace/app. */
    private boolean visibleInMarketplace;

    /** Pode ser lançado numa comanda de mesa (PDV-F010). */
    private boolean availableForTable;

    /** Vendido por sessão de mesa, não por unidade (PDV-F010). Os sabores são as variações da grade. */
    private boolean sessionProduct;

    /** Quantas sessões saem de uma unidade da origem. Nulo quando não informado. */
    private Integer sessionsPerUnit;

    /** Consumo livre por valor fixo, cobrado uma vez por sessão. Nulo quando o produto não tem open rosh. */
    private BigDecimal openRoshPrice;

    /** {@code RASCUNHO} ou {@code ATIVO} (EST-F023). */
    private String status;
}
