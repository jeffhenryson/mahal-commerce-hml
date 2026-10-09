package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.util.List;

/**
 * Produto (SKU pai) da grade de estoque. Agrega as variações (SKU filhos), cada
 * uma distinguida por seus {@link ProductAttribute} (sabor, tamanho, cor), e a
 * {@link Pricing} do SKU pai.
 *
 * <p><b>Categoria existe em dois lugares e isso é deliberado.</b> {@link #categoryId()} é o
 * vínculo com a entidade {@link Category} (que carrega destaque e ordem da vitrine);
 * {@link #category()} é o <b>nome denormalizado</b>, mantido em sincronia pelo backend. O texto
 * permanece porque é o que {@code mahal-market} e {@code mahal-admin} já leem — trocá-lo por um id
 * quebraria os dois de uma vez. O vínculo é opcional: produto legado sem categoria resolvida tem
 * {@code categoryId} nulo e segue funcionando.</p>
 *
 * <p><b>Atributos existem em dois níveis.</b> Os de {@link ProductVariant} <i>distinguem</i> uma
 * variação das outras da mesma grade (é o sabor que diferencia dois SKUs). Os do próprio produto
 * ({@link #attributes()}) apenas <i>descrevem</i> o item e não distinguem nada — servem ao produto
 * sem grade, que antes não tinha onde carregar "Sabor: Menta" por não ter variação nenhuma. São
 * coleções separadas de propósito: fundi-las faria um atributo descritivo do pai parecer parte da
 * chave que identifica a variação.</p>
 *
 * <p><b>Preço mora no SKU pai</b> (EST-F019). As variações herdam — sabores diferentes da mesma
 * essência 50g custam o mesmo. Preço por variação (ex.: tamanhos distintos do mesmo narguilé)
 * é EST-F020 no backlog do módulo; até lá, grade com preços diferentes se modela como produtos
 * separados.</p>
 *
 * <p><b>{@code status} é um eixo independente de {@code active}/{@code type}</b> (EST-F023):
 * {@code status} é "publicado ou ainda rascunho", {@code active} é "disponível para venda" e
 * {@code type} é "produto ou kit". Um rascunho pode estar {@code active=true} e mesmo assim não
 * aparecer em nenhuma vitrine, porque {@code RASCUNHO} é filtrado à parte de {@code active} pelas
 * telas que só querem catálogo publicado.</p>
 */
public record Product(
    Long id,
    String sku,
    String name,
    String category,
    boolean active,
    List<ProductVariant> variants,
    Pricing pricing,
    ProductType type,
    boolean lotTracked,
    String brand,
    String imageUrl,
    boolean onSale,
    boolean superPromo,
    String description,
    String videoUrl,
    List<String> images,
    List<ProductAttribute> attributes,
    Long categoryId,
    String barcode,
    MeasurementUnit unit,
    boolean sampleProduct,
    boolean kitComponentEligible,
    boolean visibleInPos,
    boolean visibleInMarketplace,
    ProductStatus status,
    Long brandId,
    boolean availableForTable,
    boolean sessionProduct,
    Integer sessionsPerUnit,
    BigDecimal openRoshPrice,
    boolean parentSellable
) {

    public Product {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        variants = variants == null ? List.of() : List.copyOf(variants);
        pricing = pricing == null ? Pricing.empty() : pricing;
        type = type == null ? ProductType.SIMPLES : type;
        images = images == null ? List.of() : List.copyOf(images);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        unit = unit == null ? MeasurementUnit.UN : unit;
        // EST-F023: dado legado (sem o campo) lê como ATIVO — nunca houve rascunho antes desta feature.
        status = status == null ? ProductStatus.ATIVO : status;
        if (type == ProductType.KIT && lotTracked) {
            throw new IllegalArgumentException(
                    "kit não pode ser lote-rastreado: kit não tem saldo físico próprio (EST-F015)");
        }
        if (sessionsPerUnit != null && sessionsPerUnit <= 0) {
            throw new IllegalArgumentException(
                    "sessionsPerUnit deve ser maior que zero quando informado: " + sessionsPerUnit);
        }
        if (openRoshPrice != null && openRoshPrice.signum() < 0) {
            throw new IllegalArgumentException("openRoshPrice não pode ser negativo: " + openRoshPrice);
        }
    }

    /** Cria um novo produto sem precificação (sem id, ativo por padrão, {@code SIMPLES}). */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants) {
        return create(sku, name, category, variants, Pricing.empty());
    }

    /** Cria um novo produto precificado (sem id, ativo por padrão, {@code SIMPLES}). */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing) {
        return create(sku, name, category, variants, pricing, ProductType.SIMPLES);
    }

    /** Cria um novo produto (sem id, ativo por padrão) — tipo explícito, sem rastreio de lote. */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, ProductType type) {
        return create(sku, name, category, variants, pricing, type, false);
    }

    /** Cria um novo produto (sem id, ativo por padrão) — tipo e lote explícitos, sem marca. */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, ProductType type, boolean lotTracked) {
        return create(sku, name, category, variants, pricing, type, lotTracked, null);
    }

    /** Cria um novo produto (sem id, ativo por padrão) — tipo e lote explícitos, com marca, sem imagem/promoção. */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, ProductType type, boolean lotTracked, String brand) {
        return create(sku, name, category, variants, pricing, type, lotTracked, brand, null, false);
    }

    /**
     * Cria um novo produto (sem id, ativo por padrão) — com marca, imagem cadastrada
     * manualmente e sinalização de promoção (Estágio 01 do admin), sem os campos de
     * marketing adicionados depois (super promo, descrição, vídeo, galeria).
     */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, ProductType type, boolean lotTracked, String brand, String imageUrl, boolean onSale) {
        return create(sku, name, category, variants, pricing, type, lotTracked, brand, imageUrl, onSale, false, null,
                null, List.of());
    }

    /**
     * Cria um novo produto (sem id, ativo por padrão) — forma canônica, com marca, imagem,
     * promoção, selo de super promoção, descrição, vídeo e galeria de imagens.
     */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, ProductType type, boolean lotTracked, String brand, String imageUrl, boolean onSale,
            boolean superPromo, String description, String videoUrl, List<String> images) {
        return create(sku, name, category, variants, pricing, type, lotTracked, brand, imageUrl, onSale, superPromo,
                description, videoUrl, images, List.of());
    }

    /**
     * Cria um novo produto (sem id, ativo por padrão) — forma canônica, incluindo os atributos do
     * próprio SKU pai. Ver {@link #attributes()} para por que eles existem separados dos da
     * variação.
     */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, ProductType type, boolean lotTracked, String brand, String imageUrl, boolean onSale,
            boolean superPromo, String description, String videoUrl, List<String> images,
            List<ProductAttribute> attributes) {
        return create(sku, name, category, variants, pricing, type, lotTracked, brand, imageUrl, onSale, superPromo,
                description, videoUrl, images, attributes, null, null);
    }

    /**
     * Cria um novo produto (sem id, ativo por padrão) — forma canônica, incluindo o vínculo com as
     * entidades {@link Category} e {@link Brand}. Ver {@link #categoryId()}/{@link #brandId()}.
     *
     * <p>Sempre nasce {@link ProductStatus#ATIVO} — um rascunho é obtido chamando
     * {@link #withStatus(ProductStatus)} depois de criar, decisão que cabe a
     * {@code EstoqueService}, não ao domínio.</p>
     */
    public static Product create(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, ProductType type, boolean lotTracked, String brand, String imageUrl, boolean onSale,
            boolean superPromo, String description, String videoUrl, List<String> images,
            List<ProductAttribute> attributes, Long categoryId, Long brandId) {
        return new Product(null, sku, name, category, true, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId,
                null, null, false, false, true, true, ProductStatus.ATIVO, brandId, true, false, null, null, false);
    }

    /** Reconstitui um produto sem precificação a partir de persistência. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants) {
        return of(id, sku, name, category, active, variants, Pricing.empty());
    }

    /** Reconstitui um produto a partir de persistência. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing) {
        return of(id, sku, name, category, active, variants, pricing, ProductType.SIMPLES);
    }

    /** Reconstitui um produto a partir de persistência — tipo explícito, sem rastreio de lote. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type) {
        return of(id, sku, name, category, active, variants, pricing, type, false);
    }

    /** Reconstitui um produto a partir de persistência — tipo e lote explícitos, sem marca. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked) {
        return of(id, sku, name, category, active, variants, pricing, type, lotTracked, null);
    }

    /** Reconstitui um produto a partir de persistência — com marca, sem imagem/promoção. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked, String brand) {
        return of(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, null, false);
    }

    /**
     * Reconstitui um produto a partir de persistência — com marca, imagem e sinalização de
     * promoção (Estágio 01 do admin), sem os campos de marketing adicionados depois (super
     * promo, descrição, vídeo, galeria).
     */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked, String brand,
            String imageUrl, boolean onSale) {
        return of(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl, onSale,
                false, null, null, List.of());
    }

    /**
     * Reconstitui um produto a partir de persistência — forma canônica, com marca, imagem,
     * promoção, selo de super promoção, descrição, vídeo e galeria de imagens.
     */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked, String brand,
            String imageUrl, boolean onSale, boolean superPromo, String description, String videoUrl,
            List<String> images) {
        return of(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl, onSale,
                superPromo, description, videoUrl, images, List.of());
    }

    /** Reconstitui um produto a partir de persistência — forma canônica, com atributos do SKU pai. */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked, String brand,
            String imageUrl, boolean onSale, boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes) {
        return of(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl, onSale,
                superPromo, description, videoUrl, images, attributes, null);
    }

    /**
     * Reconstitui um produto a partir de persistência — forma canônica, com o vínculo de
     * categoria, sem {@code status} explícito (dado legado, lê como {@link ProductStatus#ATIVO}
     * pelo compact constructor). Não é o caminho usado por {@code ProductRepositoryImpl} — ver a
     * sobrecarga abaixo.
     */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked, String brand,
            String imageUrl, boolean onSale, boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes, Long categoryId) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId,
                null, null, false, false, true, true, null, null, true, false, null, null, false);
    }

    /**
     * Reconstitui um produto a partir de persistência — forma canônica completa, com todos os
     * campos aditivos (código de barras, unidade, testador, elegibilidade de kit, visibilidade
     * por canal, status de publicação), <b>sem</b> os campos de mesa/sessão (PDV-F010): resolvem
     * para o mesmo default da migration — disponível na mesa, não vendido por sessão.
     */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked, String brand,
            String imageUrl, boolean onSale, boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible, boolean visibleInPos,
            boolean visibleInMarketplace, ProductStatus status, Long brandId) {
        return of(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId,
                true, false, null, null);
    }

    /**
     * Reconstitui um produto a partir de persistência — forma canônica completa <b>com</b> os
     * campos de mesa e de sessão de narguilé (PDV-F010). Único ponto de reconstituição usado por
     * {@code ProductRepositoryImpl}.
     */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked, String brand,
            String imageUrl, boolean onSale, boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible, boolean visibleInPos,
            boolean visibleInMarketplace, ProductStatus status, Long brandId, boolean availableForTable,
            boolean sessionProduct, Integer sessionsPerUnit, BigDecimal openRoshPrice) {
        return of(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl, onSale,
                superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit, sampleProduct,
                kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable,
                sessionProduct, sessionsPerUnit, openRoshPrice, false);
    }

    /**
     * Reconstitui um produto a partir de persistência com {@code parentSellable} (EST-F036, V149) —
     * a forma que {@code ProductRepositoryImpl} usa.
     */
    public static Product of(Long id, String sku, String name, String category, boolean active,
            List<ProductVariant> variants, Pricing pricing, ProductType type, boolean lotTracked, String brand,
            String imageUrl, boolean onSale, boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible, boolean visibleInPos,
            boolean visibleInMarketplace, ProductStatus status, Long brandId, boolean availableForTable,
            boolean sessionProduct, Integer sessionsPerUnit, BigDecimal openRoshPrice, boolean parentSellable) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId,
                availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Alteração parcial (EST-F018): argumento nulo significa <b>não mexer neste campo</b>.
     *
     * <p>Consequência conhecida: como {@code null} quer dizer "manter", não há como <b>limpar</b>
     * a {@code category}/{@code brand} por este caminho — o máximo é trocá-la. É o custo da
     * semântica de PATCH sem um wrapper de três estados (ausente / nulo / valor).</p>
     *
     * <p>{@code sku} não entra: é a identidade do produto e aparece como texto livre em
     * {@code stock_balance}, {@code stock_movement} e {@code stock_reorder_point}, sem FK.
     * Renomear o SKU aqui transformaria em órfão todo o histórico do produto (EST-C011).
     * {@code variants} também não entra — mexer na grade altera o espaço de nomes de SKU e
     * precisa da mesma validação de duplicidade de {@code createProduct}.</p>
     */
    public Product withDetails(String newName, String newCategory) {
        return withDetails(newName, newCategory, null);
    }

    /** Variante de {@link #withDetails(String, String)} que também alcança {@code brand}. */
    public Product withDetails(String newName, String newCategory, String newBrand) {
        return withDetails(newName, newCategory, newBrand, null);
    }

    /** Variante de {@link #withDetails(String, String, String)} que também alcança {@code imageUrl}. */
    public Product withDetails(String newName, String newCategory, String newBrand, String newImageUrl) {
        return withDetails(newName, newCategory, newBrand, newImageUrl, null, null);
    }

    /**
     * Variante de {@link #withDetails(String, String, String, String)} que também alcança
     * {@code description} e {@code videoUrl}. {@code images} fica de fora: é substituição de
     * coleção, não escalar "nulo mantém" — ver {@link #withImages(List)}.
     */
    public Product withDetails(String newName, String newCategory, String newBrand, String newImageUrl,
            String newDescription, String newVideoUrl) {
        return new Product(id, sku,
                newName == null ? name : newName,
                newCategory == null ? category : newCategory,
                active, variants, pricing, type, lotTracked,
                newBrand == null ? brand : newBrand,
                newImageUrl == null ? imageUrl : newImageUrl,
                onSale, superPromo,
                newDescription == null ? description : newDescription,
                newVideoUrl == null ? videoUrl : newVideoUrl,
                images, attributes, categoryId, barcode, unit, sampleProduct, kitComponentEligible, visibleInPos,
                visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /** Ativa ou desativa o produto, preservando o resto. */
    public Product withActive(boolean newActive) {
        return new Product(id, sku, name, category, newActive, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Publica ou rebaixa a rascunho, preservando o resto (EST-F023). Ortogonal a
     * {@link #active()}: um rascunho pode estar ativo e ainda assim ficar fora das vitrines que
     * filtram por {@code status=ATIVO}.
     */
    public Product withStatus(ProductStatus newStatus) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, newStatus, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /** Substitui a precificação do produto, preservando o resto. */
    public Product withPricing(Pricing newPricing) {
        return new Product(id, sku, name, category, active, variants, newPricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Promove/rebaixa o tipo do produto, preservando o resto. Usado só por
     * {@code EstoqueService.defineKitRecipe} (EST-F015) — não há endpoint que troque o tipo
     * isoladamente, porque virar {@code KIT} sem uma receita não faz sentido.
     */
    public Product withType(ProductType newType) {
        return new Product(id, sku, name, category, active, variants, pricing, newType, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Ativa/desativa o rastreamento de lote e validade (EST-F008). Opt-in por produto: só
     * essência/perecível precisa — narguilé e acessório não têm validade. Kit nunca pode ser
     * lote-rastreado (kit não tem saldo físico próprio; checado no compact constructor).
     */
    public Product withLotTracked(boolean newLotTracked) {
        return new Product(id, sku, name, category, active, variants, pricing, type, newLotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /** Marca ou desmarca o produto como em promoção (Estágio 01 do admin), preservando o resto. */
    public Product withOnSale(boolean newOnSale) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                newOnSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /** Marca ou desmarca o produto com o selo de super promoção, preservando o resto. */
    public Product withSuperPromo(boolean newSuperPromo) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, newSuperPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Substitui a galeria de imagens inteira, preservando o resto. Sem edição parcial
     * (adicionar/remover uma imagem isolada) — o chamador sempre envia a lista completa.
     */
    public Product withImages(List<String> newImages) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, newImages, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Substitui os atributos do próprio SKU pai, preservando o resto. Como {@link #withImages},
     * é substituição da coleção inteira — não há edição de um atributo isolado.
     */
    public Product withAttributes(List<ProductAttribute> newAttributes) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, newAttributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Vincula o produto a uma {@link Category}, mantendo o nome denormalizado em sincronia.
     *
     * <p>Os dois andam sempre juntos, de propósito: {@code category} (texto) é o que
     * {@code mahal-market} e {@code mahal-admin} leem hoje, e deixá-lo divergir do nome da
     * categoria vinculada faria a vitrine exibir um rótulo e ordenar por outro.</p>
     */
    public Product withCategory(Long newCategoryId, String newCategoryName) {
        return new Product(id, sku, name,
                newCategoryName == null ? category : newCategoryName,
                active, variants, pricing, type, lotTracked, brand, imageUrl, onSale, superPromo, description,
                videoUrl, images, attributes, newCategoryId, barcode, unit, sampleProduct, kitComponentEligible,
                visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Vincula o produto a uma {@link Brand}, mantendo o nome denormalizado ({@code brand}) em
     * sincronia — mesmo par aditivo de {@link #withCategory(Long, String)}.
     */
    public Product withBrand(Long newBrandId, String newBrandName) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked,
                newBrandName == null ? brand : newBrandName,
                imageUrl, onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, newBrandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /** Define o código de barras/EAN do produto, preservando o resto. {@code null} limpa o campo. */
    public Product withBarcode(String newBarcode) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, newBarcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /** Define a unidade de medida do produto, preservando o resto. */
    public Product withUnit(MeasurementUnit newUnit) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, newUnit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Marca/desmarca o produto como testador/amostra — distinto de produto padrão da Mahal.
     * Ortogonal a {@link #type()}: sem relação com {@code SIMPLES}/{@code KIT}.
     */
    public Product withSampleProduct(boolean newSampleProduct) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                newSampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Marca/desmarca o produto como elegível para entrar como componente de um kit — opt-in,
     * checado por {@code EstoqueService.defineKitRecipe}.
     */
    public Product withKitComponentEligible(boolean newKitComponentEligible) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, newKitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /** Controla se o produto aparece no PDV, preservando o resto. */
    public Product withVisibleInPos(boolean newVisibleInPos) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, newVisibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /** Controla se o produto aparece no marketplace/app, preservando o resto. */
    public Product withVisibleInMarketplace(boolean newVisibleInMarketplace) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, newVisibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Controla se o produto pode ser lançado numa comanda de mesa (PDV-F010), preservando o resto.
     *
     * <p>Ortogonal a {@link #visibleInPos()}: bebida e narguilé saem na mesa e no balcão; cigarro
     * e isqueiro saem só no balcão. Default {@code true} — o catálogo já cadastrado sai na mesa,
     * mesma escolha de {@code visibleInPos} na V91.</p>
     */
    public Product withAvailableForTable(boolean newAvailableForTable) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId,
                newAvailableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Marca/desmarca o produto como vendido por <b>sessão de mesa</b>, não por unidade (PDV-F010),
     * preservando o resto.
     *
     * <p>A essência avulsa do balcão e a sessão de narguilé são <b>produtos distintos</b> no
     * catálogo, não dois preços do mesmo SKU: os sabores da sessão são as variações da grade, e o
     * {@code salePrice} de cada variação <b>é</b> o preço de sessão daquele sabor.</p>
     */
    public Product withSessionProduct(boolean newSessionProduct) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId,
                availableForTable, newSessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Quantas sessões saem de uma unidade da origem, preservando o resto. {@code null} = não
     * informado.
     *
     * <p><b>Não movimenta saldo sozinho</b>: é sugestão para o diálogo de conversão de estoque do
     * admin, que faz saída de 1 lata e entrada de N sessões por {@code POST /estoque/movements}.
     * Lata e sessão são SKUs distintos porque toda quantidade do sistema é inteira — baixar
     * {@code 1/N} por sessão mudaria o contrato de ajuste, contagem e reposição de uma vez.</p>
     */
    public Product withSessionsPerUnit(Integer newSessionsPerUnit) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId,
                availableForTable, sessionProduct, newSessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Preço do consumo livre (<i>open rosh</i>) — cobrado <b>uma única vez</b> por sessão, com
     * trocas de sabor ilimitadas. {@code null} = este produto não oferece consumo livre.
     *
     * <p>Mora no <b>SKU pai</b>, não na variação, e é isso que o torna a armadilha da feature: a
     * linha da comanda chega com o SKU da variação (para saber qual essência sair do estoque), mas
     * o valor cobrado é este. Resolver o preço pelo SKU, como nos outros modos, cobraria o da
     * variação.</p>
     */
    public Product withOpenRoshPrice(BigDecimal newOpenRoshPrice) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId,
                availableForTable, sessionProduct, sessionsPerUnit, newOpenRoshPrice, parentSellable);
    }

    /**
     * Substitui a grade de variações inteira, preservando o resto (EST-F024).
     *
     * <p>Wither genérico de troca — quem decide se a nova lista é um "anexar" (grade antiga +
     * novas) ou uma "edição no lugar" (grade antiga com uma delas trocada) é o chamador
     * ({@code EstoqueService.addVariants}/{@code updateVariant}), nunca este método. O record não
     * sabe, e não precisa saber, a intenção por trás da lista que recebe — só preserva a
     * invariante de que {@code save()} faz um rebuild completo da coleção (EST-C011): cada SKU de
     * variação já existente precisa estar presente aqui com o mesmo {@code id}, ou vira remoção
     * silenciosa (órfão em {@code stock_balance}/{@code stock_movement}, que referenciam o SKU
     * como texto livre).</p>
     */
    public Product withVariants(List<ProductVariant> newVariants) {
        return new Product(id, sku, name, category, active, newVariants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId, availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, parentSellable);
    }

    /**
     * Precedência de preço variação → pai (EST-F020).
     *
     * <p>A herança é <b>por campo</b>, e não tudo-ou-nada: cada valor preenchido na variação
     * vence o do pai, e cada valor ausente é herdado. É a mesma semântica de "nulo mantém" que
     * {@link Pricing#withPatch} já implementa para o PATCH, reaproveitada aqui.</p>
     *
     * <p>Tudo-ou-nada seria pior de um jeito silencioso: uma variação que declara só o próprio
     * {@code originalPrice} para efeito de vitrine passaria a não ter preço de venda nenhum,
     * porque o do pai teria sido descartado junto.</p>
     *
     * <p>Para o SKU do próprio pai, ou para uma variação que não declarou preço algum, o
     * resultado é exatamente a {@link #pricing()} do pai — o comportamento histórico do módulo,
     * que segue sendo o padrão.</p>
     *
     * <p>Mora aqui, e não no service, porque tanto a resolução de preço do PDV quanto a vitrine
     * pública precisam da mesma regra — duplicá-la seria garantir que as duas divergissem.</p>
     */
    public Pricing effectivePricingFor(String sku) {
        return variants.stream()
                .filter(variant -> variant.sku().equals(sku))
                .filter(ProductVariant::hasOwnPricing)
                .map(ProductVariant::pricing)
                .findFirst()
                .map(own -> pricing.withPatch(own.costPrice(), own.markupPercent(), own.salePrice(),
                        own.originalPrice(), own.causeAmount()))
                .orElse(pricing);
    }

    /**
     * EST-F036 — liga ou desliga a venda do SKU <b>base</b> de um produto com variações. Só tem efeito
     * quando há variações: sem elas a base é o próprio item da prateleira.
     */
    public Product withParentSellable(boolean newParentSellable) {
        return new Product(id, sku, name, category, active, variants, pricing, type, lotTracked, brand, imageUrl,
                onSale, superPromo, description, videoUrl, images, attributes, categoryId, barcode, unit,
                sampleProduct, kitComponentEligible, visibleInPos, visibleInMarketplace, status, brandId,
                availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice, newParentSellable);
    }

    /**
     * EST-F036 — {@code sku} (deste produto) pode ser vendido e receber estoque? A variação sempre;
     * a base, só se o produto não tem variações ou se {@link #parentSellable} foi ligado. É o que
     * impede "LM" de aparecer como terceiro item ao lado de "LM azul" e "LM vermelho" sem existir
     * na prateleira.
     */
    public boolean isSellable(String sku) {
        return !this.sku.equals(sku) || variants.isEmpty() || parentSellable;
    }

    /** Indica se este produto é um kit virtual (EST-F015) — sem saldo próprio, saldo derivado. */
    public boolean isKit() {
        return type == ProductType.KIT;
    }

    /** Indica se este produto/kit ainda é rascunho — fora das vitrines que filtram por publicado (EST-F023). */
    public boolean isDraft() {
        return status == ProductStatus.RASCUNHO;
    }
}
