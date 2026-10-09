package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.AbcAnalysis;
import com.cernecommerce.core.domain.model.estoque.AttributeType;
import com.cernecommerce.core.domain.model.estoque.Brand;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.EstoqueSummary;
import com.cernecommerce.core.domain.model.estoque.KitAvailability;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.KitComponentDetail;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.SkuPackaging;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port de entrada do domínio <b>estoque</b>.
 */
public interface EstoqueUseCase {

    /**
     * Cria um produto (SKU pai) com suas variações. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException}
     * se o SKU do produto ou de alguma variação já existir.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants) {
        return createProduct(sku, name, category, variants, Pricing.empty());
    }

    /**
     * Cria um produto precificado (EST-F019). {@code pricing} nulo equivale a
     * {@link Pricing#empty()} — produto sem preço é estado válido do catálogo.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing) {
        return createProduct(sku, name, category, variants, pricing, null);
    }

    /** Cria um produto com marca, sem imagem cadastrada nem promoção (EST-F0xx). */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand) {
        return createProduct(sku, name, category, variants, pricing, brand, null, false);
    }

    /**
     * Cria um produto com marca, imagem cadastrada manualmente e sinalização de promoção
     * (Estágio 01 do admin), sem os campos de marketing adicionados depois (super promo,
     * descrição, vídeo, galeria).
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, false, null, null,
                List.of());
    }

    /**
     * Cria um produto (forma canônica) com marca, imagem, promoção, selo de super promoção,
     * descrição, vídeo e galeria de imagens — nenhum desses campos tem regra de negócio, só
     * persiste/retorna.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale, boolean superPromo, String description,
            String videoUrl, List<String> images) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, superPromo, description,
                videoUrl, images, List.of());
    }

    /**
     * Cria um produto (forma canônica) incluindo os atributos descritivos do próprio SKU pai.
     *
     * <p>Distintos dos atributos de {@code variants}: aqueles fazem parte do que <i>identifica</i>
     * cada variação da grade, estes só <i>descrevem</i> o item e existem para o produto sem grade,
     * que não tinha onde carregá-los.</p>
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale, boolean superPromo, String description,
            String videoUrl, List<String> images, List<ProductAttribute> attributes) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, superPromo, description,
                videoUrl, images, attributes, null);
    }

    /**
     * Cria um produto (forma canônica) aceitando os <b>dois</b> caminhos de categoria.
     *
     * <p>{@code categoryId} preenchido vincula à categoria indicada e o nome sai resolvido a
     * partir dela. Ausente, {@code category} (texto) é usado para reencontrar — ou criar — a
     * categoria correspondente. É o que mantém o cadastro atual do admin, que só conhece texto
     * livre, funcionando sem nenhuma mudança.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException se
     *         {@code categoryId} for informado e não existir.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale, boolean superPromo, String description,
            String videoUrl, List<String> images, List<ProductAttribute> attributes, Long categoryId) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, superPromo, description,
                videoUrl, images, attributes, categoryId, null, null, false, false, null, null);
    }

    /**
     * Forma canônica anterior a EST-F023, sem {@code type}/{@code initialStock}: produto nasce
     * sempre {@code SIMPLES} e sem estoque inicial, exatamente como hoje.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale, boolean superPromo, String description,
            String videoUrl, List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible, Boolean visibleInPos,
            Boolean visibleInMarketplace) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, superPromo, description,
                videoUrl, images, attributes, categoryId, barcode, unit, sampleProduct, kitComponentEligible,
                visibleInPos, visibleInMarketplace, null, null, null);
    }

    /**
     * Forma canônica completa anterior à criação atômica de kit (Bloco 3.1) — delega com
     * {@code kitComponents = null}, ou seja, kit nasce sem receita (precisa de
     * {@code PUT .../kit} depois), exatamente como já funcionava.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale, boolean superPromo, String description,
            String videoUrl, List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible, Boolean visibleInPos,
            Boolean visibleInMarketplace, ProductType type, InitialStockCommand initialStock, String actorUsername) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, superPromo, description,
                videoUrl, images, attributes, categoryId, barcode, unit, sampleProduct, kitComponentEligible,
                visibleInPos, visibleInMarketplace, type, initialStock, actorUsername, null);
    }

    /**
     * Cria um produto (forma canônica completa), incluindo os campos aditivos de catálogo
     * (EST-F0xx), o tipo explícito, a entrada de estoque inicial atômica (EST-F023) e a receita de
     * kit opcional (Bloco 3.1). Único método abstrato de criação — todas as sobrecargas acima
     * delegam até aqui.
     *
     * @param visibleInPos {@code null} resolve para {@code true} (visível por padrão) — ver
     *        {@code EstoqueService.createProduct}.
     * @param visibleInMarketplace mesma regra de {@code visibleInPos}.
     * @param type {@code null} resolve para {@code SIMPLES}. {@code KIT} não pode vir com
     *        {@code variants} não vazio nem com {@code initialStock} — kit não tem grade nem
     *        saldo próprio, é sempre derivado dos componentes.
     * @param initialStock opcional; quando informado, registra a entrada de estoque na MESMA
     *        transação da criação do produto — evita o estado "produto criado, estoque não" de
     *        duas chamadas separadas. {@code null} preserva o comportamento anterior (produto
     *        nasce sem nenhum saldo).
     * @param actorUsername usuário autenticado, gravado como {@code username} da movimentação
     *        quando {@code initialStock} é informado. Ignorado quando {@code initialStock} é nulo.
     * @param kitComponents receita do kit, opcional, só considerada quando {@code type == KIT}.
     *        Ausente ou vazia preserva o comportamento anterior (kit nasce sem receita). Presente
     *        e não vazia é validada com as mesmas regras de {@link #defineKitRecipe} e persistida
     *        na MESMA transação da criação — fecha a janela de "kit órfão sem receita" do fluxo
     *        POST-depois-PUT.
     * @throws com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException se
     *         {@code categoryId} for informado e não existir.
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException se
     *         {@code barcode} já existir no catálogo (pai ou variação).
     * @throws com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException se
     *         {@code type == KIT} e {@code variants} não for vazio.
     * @throws com.cernecommerce.core.domain.exception.estoque.KitInitialStockNotAllowedException
     *         se {@code type == KIT} e {@code initialStock} for informado.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale, boolean superPromo, String description,
            String videoUrl, List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible, Boolean visibleInPos,
            Boolean visibleInMarketplace, ProductType type, InitialStockCommand initialStock, String actorUsername,
            List<KitComponentCommand> kitComponents) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, superPromo, description,
                videoUrl, images, attributes, categoryId, barcode, unit, sampleProduct, kitComponentEligible,
                visibleInPos, visibleInMarketplace, type, initialStock, actorUsername, kitComponents, null, null,
                null);
    }

    /**
     * Cria um produto (forma canônica completa, EST-F023), incluindo o {@code status} de
     * publicação e o vínculo com a entidade {@link com.cernecommerce.core.domain.model.estoque.Brand}.
     * Único método abstrato de criação — todas as sobrecargas acima delegam até aqui.
     *
     * @param status {@code null} resolve para {@link ProductStatus#ATIVO}. {@code RASCUNHO} é
     *        validado contra o teto de 5 rascunhos por {@code EstoqueService}, sem exigir nenhum
     *        campo além de {@code sku}/{@code name} — a mesma validação mínima que produto
     *        {@code ATIVO} já tem hoje.
     * @param brandId vínculo direto com uma marca existente (mesmo par de {@code categoryId}).
     *        Quando informado, vence sobre {@code brand} e o nome é resolvido a partir dele.
     */
    Product createProduct(String sku, String name, String category, List<ProductVariant> variants, Pricing pricing,
            String brand, String imageUrl, boolean onSale, boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible, Boolean visibleInPos,
            Boolean visibleInMarketplace, ProductType type, InitialStockCommand initialStock, String actorUsername,
            List<KitComponentCommand> kitComponents, ProductStatus status, Long brandId,
            TableSessionCommand tableSession);

    /**
     * Estoque inicial informado na criação do produto (EST-F023). {@code quantity} estritamente
     * positiva — é sempre um {@code ENTRADA}, ao contrário de {@code adjustStock}, que também
     * aceita {@code AJUSTE} com valor zero.
     */
    record InitialStockCommand(String warehouseCode, BigDecimal quantity, String lotCode, LocalDate expiryDate) {
    }

    /**
     * Campos de mesa e de sessão de narguilé do produto (PDV-F010), agrupados por um motivo
     * prático: {@code createProduct} já carrega parâmetros demais, e os quatro andam juntos —
     * quem marca {@code sessionProduct} é quem preenche {@code sessionsPerUnit} e
     * {@code openRoshPrice}. Mesmo idioma de {@link InitialStockCommand}.
     *
     * <p>Todos {@code Boolean}/wrapper: no {@code create}, {@code null} resolve para o default
     * ({@code availableForTable = true}, {@code sessionProduct = false}); no {@code update},
     * {@code null} significa <b>não mexer neste campo</b>, mesma semântica de PATCH do resto do
     * DTO. O próprio comando pode vir nulo — é o caso de quem não mexe em nada disso.</p>
     */
    record TableSessionCommand(Boolean availableForTable, Boolean sessionProduct, Integer sessionsPerUnit,
            BigDecimal openRoshPrice) {

        public static final TableSessionCommand EMPTY = new TableSessionCommand(null, null, null, null);

        /** Nunca devolve nulo — poupa o chamador de um {@code if} por campo. */
        public static TableSessionCommand orEmpty(TableSessionCommand command) {
            return command == null ? EMPTY : command;
        }
    }

    /** Lista produtos paginados, sem filtro e ordenados por id. */
    default PageResult<Product> listProducts(int page, int size) {
        return listProducts(page, size, ProductFilter.EMPTY, ProductSortField.ID, SortDirection.ASC);
    }

    /**
     * Lista produtos paginados aplicando busca, filtros e ordenação <b>no banco</b>.
     *
     * <p>Existe porque a versão só-paginada obrigava o cliente a baixar o catálogo inteiro para
     * filtrar em memória. Como o teto de {@code size} é 100 (EST-C005), catálogo maior que isso
     * fazia busca, KPI e exportação do admin passarem a mentir em silêncio: os 100 primeiros
     * sempre voltam, então não há erro visível — só resultado incompleto.</p>
     *
     * @param filter critérios opcionais; {@link ProductFilter#EMPTY} não filtra nada.
     * @param sortField campo de ordenação, sempre desempatado por id (EST-C012).
     */
    PageResult<Product> listProducts(int page, int size, ProductFilter filter,
            ProductSortField sortField, SortDirection direction);

    /**
     * Produtos ativos e precificados, paginados — a consulta que o catálogo público consome
     * (ECM-F002). Filtra no banco, não em memória: paginar depois de filtrar em memória devolveria
     * contagem de página errada.
     *
     * @param onSale filtro opcional de promoção (Estágio 01 do admin) — {@code null} não filtra.
     */
    default PageResult<Product> listActivePricedProducts(int page, int size, Boolean onSale) {
        return listActivePricedProducts(page, size, onSale, null);
    }

    /**
     * @param categoryId filtro opcional de categoria — {@code null} não filtra. A ordem é a da
     *        vitrine: categoria em destaque primeiro, depois ordem de exibição, depois id.
     */
    default PageResult<Product> listActivePricedProducts(int page, int size, Boolean onSale, Long categoryId) {
        return listActivePricedProducts(page, size, onSale, categoryId, null);
    }

    /**
     * @param search busca livre opcional (EST-F029) — nome, SKU, categoria ou marca; digitar o
     *        nome de uma categoria traz a categoria inteira. {@code null} não filtra.
     */
    PageResult<Product> listActivePricedProducts(int page, int size, Boolean onSale, Long categoryId, String search);

    /**
     * Alteração parcial de produto (EST-F018): {@code name} e/ou {@code category} nulos são
     * mantidos como estão. Não altera {@code sku} — a troca tem operação própria,
     * {@link #changeSku(String, String)} (EST-F030) — nem as variações. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * não for um SKU pai existente.
     */
    default Product updateProduct(String sku, String name, String category) {
        return updateProduct(sku, name, category, null);
    }

    /**
     * Alteração parcial incluindo precificação (EST-F019). {@code pricing} nulo mantém a
     * precificação atual; se vier preenchido, cada um dos seus três campos segue a mesma
     * semântica de PATCH — nulo mantém, valor troca (ver {@link Pricing#withPatch}).
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing) {
        return updateProduct(sku, name, category, pricing, null);
    }

    /**
     * Alteração parcial incluindo marca, sem mexer em imagem ou promoção. {@code brand} nulo
     * mantém a marca atual — mesma semântica de PATCH de {@code name}/{@code category}.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand) {
        return updateProduct(sku, name, category, pricing, brand, null, null);
    }

    /**
     * Alteração parcial incluindo imagem cadastrada manualmente e sinalização de promoção
     * (Estágio 01 do admin), sem os campos de marketing adicionados depois (super promo,
     * descrição, vídeo, galeria). {@code imageUrl} nulo mantém a atual — mesma semântica de
     * {@code brand}. {@code onSale} nulo mantém a sinalização atual; {@code true}/{@code false}
     * explícitos trocam — é um toggle, não um "nulo mantém" de String.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand,
            String imageUrl, Boolean onSale) {
        return updateProduct(sku, name, category, pricing, brand, imageUrl, onSale, null, null, null, null);
    }

    /**
     * Alteração parcial (forma canônica), incluindo selo de super promoção, descrição, vídeo e
     * galeria de imagens. {@code superPromo} segue a mesma semântica de toggle de {@code onSale}
     * (nulo mantém, valor explícito troca). {@code description}/{@code videoUrl} nulos mantêm o
     * valor atual — mesma semântica de {@code brand}/{@code imageUrl}. {@code images} nulo
     * mantém a galeria atual; uma lista (mesmo vazia) substitui a galeria inteira — sem edição
     * parcial de item individual.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand,
            String imageUrl, Boolean onSale, Boolean superPromo, String description, String videoUrl,
            List<String> images) {
        return updateProduct(sku, name, category, pricing, brand, imageUrl, onSale, superPromo, description, videoUrl,
                images, null);
    }

    /**
     * Alteração parcial (forma canônica) incluindo os atributos do próprio SKU pai.
     * {@code attributes} nulo mantém os atuais; uma lista (mesmo vazia) substitui o conjunto
     * inteiro — a mesma semântica já adotada para {@code images}, e pelo mesmo motivo: não há
     * identidade estável por atributo que permitisse edição item a item.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand,
            String imageUrl, Boolean onSale, Boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes) {
        return updateProduct(sku, name, category, pricing, brand, imageUrl, onSale, superPromo, description, videoUrl,
                images, attributes, null);
    }

    /**
     * Alteração parcial (forma canônica) aceitando os dois caminhos de categoria, com a mesma
     * regra de {@code createProduct}. Nulos nos dois campos mantêm a categoria atual.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand,
            String imageUrl, Boolean onSale, Boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes, Long categoryId) {
        return updateProduct(sku, name, category, pricing, brand, imageUrl, onSale, superPromo, description, videoUrl,
                images, attributes, categoryId, null, null, null, null, null, null);
    }

    /**
     * Alteração parcial (forma canônica completa), incluindo os campos aditivos de catálogo:
     * código de barras, unidade de medida, testador, elegibilidade de kit e visibilidade por
     * canal. Todos seguem a mesma semântica de PATCH do resto do método — {@code null} mantém.
     * Único método abstrato de alteração — todas as sobrecargas acima delegam até aqui.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException se
     *         {@code barcode} já existir em outro produto do catálogo.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand,
            String imageUrl, Boolean onSale, Boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, Boolean sampleProduct, Boolean kitComponentEligible, Boolean visibleInPos,
            Boolean visibleInMarketplace) {
        return updateProduct(sku, name, category, pricing, brand, imageUrl, onSale, superPromo, description, videoUrl,
                images, attributes, categoryId, barcode, unit, sampleProduct, kitComponentEligible, visibleInPos,
                visibleInMarketplace, null, null, null);
    }

    /**
     * Alteração parcial (forma canônica completa, EST-F023), incluindo {@code status} e o vínculo
     * com {@link com.cernecommerce.core.domain.model.estoque.Brand}. {@code null} mantém o status
     * atual — mesma semântica de PATCH do resto do método. Único método abstrato de alteração —
     * todas as sobrecargas acima delegam até aqui.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException se
     *         {@code barcode} já existir em outro produto do catálogo.
     * @throws com.cernecommerce.core.domain.exception.estoque.DraftLimitReachedException se a
     *         transição for para {@code RASCUNHO} e o produto já não for um rascunho, com 5
     *         rascunhos já cadastrados.
     * @param brandId vínculo direto com uma marca existente. Nulos em {@code brandId}/
     *        {@code brand} mantêm a marca atual — mesma semântica de {@code categoryId}/
     *        {@code category}.
     */
    Product updateProduct(String sku, String name, String category, Pricing pricing, String brand, String imageUrl,
            Boolean onSale, Boolean superPromo, String description, String videoUrl, List<String> images,
            List<ProductAttribute> attributes, Long categoryId, String barcode, MeasurementUnit unit,
            Boolean sampleProduct, Boolean kitComponentEligible, Boolean visibleInPos, Boolean visibleInMarketplace,
            ProductStatus status, Long brandId, TableSessionCommand tableSession);

    /**
     * Troca o SKU de um produto pai ou de uma variação (EST-F030), propagando para todo o sistema
     * — saldo, lotes, reservas, carrinhos, comandas, pedidos, compras, NF-e e receitas de kit —
     * inclusive o histórico, para relatório por SKU continuar batendo antes e depois da troca.
     *
     * @return o produto pai já com o SKU novo (se a troca foi de variação, é o pai dela)
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se
     *         {@code currentSku} não existe no catálogo
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException se
     *         {@code newSku} já é SKU de outro produto ou variação
     */
    Product changeSku(String currentSku, String newSku);

    /**
     * Acrescenta uma ou mais variações novas à grade de um produto já existente (EST-F024).
     * Puramente aditivo — nenhuma variação já cadastrada é alterada ou removida.
     *
     * <p>Deliberadamente não há caminho de substituição/remoção em massa da grade: a coleção é
     * reconstruída inteira a cada save (EST-C011), e uma variação já existente omitida da lista
     * viraria uma remoção silenciosa — órfão em {@code stock_balance}/{@code stock_movement}, que
     * referenciam o SKU como texto livre, sem FK.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se
     *         {@code sku} não for um SKU pai existente.
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException se algum SKU
     *         novo já existir no catálogo (pai ou variação, inclusive repetido dentro do próprio
     *         request).
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException se algum
     *         código de barras novo já existir no catálogo.
     * @throws com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException se
     *         {@code sku} for um {@code KIT} — kit não tem grade (EST-F015).
     */
    Product addVariants(String sku, List<ProductVariant> newVariants);

    /**
     * Altera parcialmente uma variação já existente, sem tocar nas demais (EST-F024). Mesma
     * semântica de PATCH do resto do módulo — {@code null} mantém; {@code attributes} (mesmo
     * vazia) substitui o conjunto inteiro, igual a {@code updateProduct}. Não altera o SKU da
     * variação — mesmo motivo de {@code updateProduct} não alterar o SKU do produto.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se
     *         {@code productSku} não for um SKU pai existente.
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductVariantNotFoundException se
     *         {@code variantSku} não existir na grade de {@code productSku}.
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException se o novo
     *         código de barras já existir em outra variação/produto do catálogo.
     */
    Product updateVariant(String productSku, String variantSku, Boolean active, List<ProductAttribute> attributes,
            Pricing pricing, String barcode);

    /**
     * Remove de fato uma variação da grade (item 8 do pedido do frontend) — para o caso concreto
     * de variante criada por engano; {@code active:false} via {@link #updateVariant} continua
     * sendo o caminho recomendado para retirar uma variante de circulação preservando histórico.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se
     *         {@code productSku} não for um SKU pai existente.
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductVariantNotFoundException se
     *         {@code variantSku} não existir na grade de {@code productSku}.
     * @throws com.cernecommerce.core.domain.exception.estoque.VariantHasStockHistoryException se
     *         houver saldo ou movimentação de estoque gravados para o SKU da variante — apagar
     *         deixaria esse histórico órfão, já que {@code stock_balance}/{@code stock_movement}
     *         referenciam o SKU como texto livre, sem FK.
     */
    Product deleteVariant(String productSku, String variantSku);

    /**
     * Descarta um <b>rascunho</b> de produto/kit (EST-F026) — o pedido EST-020 do QA do
     * {@code frontend-admin-prod}.
     *
     * <p>Existe porque o teto de 5 rascunhos (EST-F023) orientava uma ação que o sistema não
     * oferecia: o 409 diz "publique ou remova um rascunho", e remover não existia.
     * {@code PATCH .../active} com {@code active:false} <b>não</b> libera a vaga — {@code status} e
     * {@code active} são eixos independentes —, então a única saída era publicar no catálogo um
     * produto que o operador não queria publicar. Cinco rascunhos abandonados desligavam o recurso
     * para o tenant inteiro.</p>
     *
     * <p><b>Só rascunho.</b> Produto publicado responde 409 e continua saindo de circulação por
     * {@code active:false}: exclusão de catálogo deixaria órfão o histórico que referencia o SKU
     * como texto livre, sem FK (EST-C011).</p>
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir.
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotDraftException se o
     *         produto não estiver em {@code RASCUNHO}.
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductHasStockHistoryException se
     *         houver saldo ou movimentação gravados para o SKU — ou para qualquer SKU da grade.
     */
    void deleteProduct(String sku);

    // ── Categorias do catálogo ───────────────────────────────────────────────
    //
    // Categoria deixou de ser só texto livre dentro do produto para poder carregar destaque e
    // ordem — sem registro próprio não há onde pendurar "aparece na primeira linha do app".
    // A mudança é ADITIVA: o campo texto do produto continua existindo e continua sendo
    // devolvido, com o nome denormalizado mantido em sincronia por estas operações.

    /**
     * Cria uma categoria. Lança {@link com.cernecommerce.core.domain.exception.estoque
     * .DuplicateCategoryNameException} se já existir uma com o mesmo nome — comparação sem
     * diferenciar maiúsculas, para "Narguilé" e "narguilé" não virarem duas.
     */
    Category createCategory(String name, boolean featured, int displayOrder);

    /**
     * Alteração parcial: campo nulo é mantido. Renomear <b>propaga</b> o novo nome para a coluna
     * denormalizada de todos os produtos vinculados — deixá-los divergir faria a vitrine exibir um
     * rótulo e ordenar por outro.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException se o id
     *         não existir.
     */
    Category updateCategory(Long id, String name, Boolean featured, Integer displayOrder);

    /**
     * Ativa ou desativa uma categoria. Desativada, ela some da vitrine
     * ({@link #listActiveCategories()}), mas os produtos vinculados <b>continuam</b> à venda com o
     * nome que já tinham: categoria é organização de vitrine, não permissão de venda. Sem DELETE,
     * pelo mesmo motivo de produto e depósito (EST-F018) — apagar destruiria o vínculo histórico.
     */
    Category setCategoryActive(Long id, boolean active);

    /** Listagem do admin, com as inativas, ordenada por destaque, ordem e nome. */
    PageResult<Category> listCategories(int page, int size);

    /** Categorias ativas na ordem da vitrine — destaque primeiro. Consumida por {@code /shop}. */
    List<Category> listActiveCategories();

    /**
     * Quantos produtos estão vinculados a cada categoria da lista, numa única consulta agregada
     * (Bloco 2.1 do BACKEND_TODO de mahal-admin) — evita N+1 ao montar {@code productCount} por
     * página de {@code GET /estoque/categories}. Categoria sem produto vinculado simplesmente não
     * aparece no mapa retornado (contagem zero, implícita).
     */
    Map<Long, Long> countProductsByCategoryIds(List<Long> categoryIds);

    /**
     * Remove uma categoria (Bloco 2.3). Bloqueada com
     * {@link com.cernecommerce.core.domain.exception.estoque.CategoryHasProductsException} se
     * houver produto vinculado — apagar deixaria {@code product.category_id} órfão contra a FK
     * sem {@code ON DELETE}.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException se o id
     *         não existir.
     */
    void deleteCategory(Long id);

    // ── Marcas do catálogo ────────────────────────────────────────────────────
    //
    // Mesmo raciocínio aditivo de Category (ver bloco acima): product.brand (texto) continua
    // existindo e continua sendo devolvido, com o nome denormalizado mantido em sincronia por
    // estas operações. Mais simples que Category: sem featured/displayOrder — marca não tem
    // pedido de destaque de vitrine.

    /**
     * Cria uma marca. Lança {@link com.cernecommerce.core.domain.exception.estoque
     * .DuplicateBrandNameException} se já existir uma com o mesmo nome — comparação sem
     * diferenciar maiúsculas, para "Zomo" e "zomo" não virarem duas.
     */
    Brand createBrand(String name);

    /**
     * Renomeia uma marca — <b>propaga</b> o novo nome para a coluna denormalizada de todos os
     * produtos vinculados.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.BrandNotFoundException se o id
     *         não existir.
     */
    Brand updateBrand(Long id, String name);

    /**
     * Ativa ou desativa uma marca. Desativada, ela some de {@link #listActiveBrands()}, mas os
     * produtos vinculados continuam à venda — mesma régua de {@link #setCategoryActive}.
     */
    Brand setBrandActive(Long id, boolean active);

    /**
     * Listagem do admin, com as inativas, ordenada por nome. {@code search} filtra por substring
     * no nome, sem diferenciar maiúsculas; {@code null}/vazio não filtra.
     */
    PageResult<Brand> listBrands(String search, int page, int size);

    /** Marcas ativas, ordenadas por nome. */
    List<Brand> listActiveBrands();

    /**
     * Quantos produtos estão vinculados a cada marca da lista, numa única consulta agregada —
     * mesmo padrão de {@link #countProductsByCategoryIds}.
     */
    Map<Long, Long> countProductsByBrandIds(List<Long> brandIds);

    /**
     * Remove uma marca. Bloqueada com
     * {@link com.cernecommerce.core.domain.exception.estoque.BrandHasProductsException} se houver
     * produto vinculado.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.BrandNotFoundException se o id não
     *         existir.
     */
    void deleteBrand(Long id);

    /**
     * Margem média (sobre a venda) dos produtos vinculados a cada categoria da lista, numa
     * consulta por categoria — mesma fórmula de {@code Pricing#marginPercent()}, promediada.
     * Categoria sem produto com margem calculável mapeia para {@code null} (contagem zero,
     * implícita, mesmo padrão de {@link #countProductsByCategoryIds}).
     */
    Map<Long, BigDecimal> averageMarginPercentByCategoryIds(List<Long> categoryIds);

    /** Mesmo que {@link #averageMarginPercentByCategoryIds}, por marca. */
    Map<Long, BigDecimal> averageMarginPercentByBrandIds(List<Long> brandIds);

    // ── Vocabulário de atributos ─────────────────────────────────────────────

    /**
     * Cadastra um novo tipo de atributo. Lança {@link com.cernecommerce.core.domain.exception
     * .estoque.DuplicateAttributeTypeNameException} se já existir um com o mesmo nome —
     * comparação sem diferenciar maiúsculas.
     */
    AttributeType createAttributeType(String name);

    /** Lista todos os tipos de atributo cadastrados, ordenados por nome. */
    List<AttributeType> listAttributeTypes();

    // ── Lista de Reposição ────────────────────────────────────────────────────
    //
    // Rascunho de compra por depósito (item 1 do pedido do frontend). Os campos de estoque
    // (currentStock, minStock, suggestedQuantity, unitCost, previousPurchase) são um SNAPSHOT
    // tirado no momento do anotar, deliberadamente não recalculado na leitura — é a intenção de
    // compra que importa, não o saldo ao vivo.

    /**
     * Anota (ou reanota — upsert por SKU) um item na lista de reposição de um depósito. Tira o
     * snapshot de produto/saldo/ponto de reposição/custo/última compra na hora da chamada.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir no catálogo.
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se o
     *         código do depósito não existir.
     */
    ReplenishmentListItem upsertReplenishmentItem(String sku, String warehouseCode, BigDecimal quantity, String note,
            String actorUsername);

    /**
     * Altera {@code quantity}/{@code note} de um item já anotado — os dois únicos campos
     * editáveis depois do POST. Não toca nos campos de snapshot.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ReplenishmentItemNotFoundException
     *         se não houver item anotado para esse SKU/depósito.
     */
    ReplenishmentListItem updateReplenishmentItem(String sku, String warehouseCode, BigDecimal quantity,
            String note);

    /** Remove um item da lista. Idempotente: não é erro remover o que não existe. */
    void deleteReplenishmentItem(String sku, String warehouseCode);

    /** Limpa a lista inteira de um depósito. */
    void clearReplenishmentList(String warehouseCode);

    /** Lista os itens anotados de um depósito, mais recentemente anotados primeiro. */
    List<ReplenishmentListItem> listReplenishmentItems(String warehouseCode);

    /**
     * Resolve a precificação vigente de <b>qualquer</b> SKU do catálogo — pai ou variação
     * (EST-F019). Variação herda o preço do pai. É a consulta que o PDV e a vitrine usam antes
     * de montar o item de venda.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException
     *         se o SKU não existir no catálogo.
     */
    Pricing findPricingBySku(String sku);

    /**
     * Nome do produto e precificação vigente de <b>qualquer</b> SKU, numa consulta só — o par que
     * uma venda precisa para congelar {@code OrderItem.productName}/{@code unitPrice}/
     * {@code costPrice} no instante da venda, sem fazer duas buscas pelo mesmo SKU
     * ({@link #findPricingBySku} e um lookup de nome separado fariam dois
     * {@code findByAnySku} idênticos).
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException
     *         se o SKU não existir no catálogo.
     */
    CatalogSaleInfo resolveSaleInfo(String sku);

    /**
     * @param productName nome do SKU pai — variação herda o nome, mesma regra de preço (EST-F019).
     * @param availableForTable se o SKU pode ser lançado numa comanda de mesa (PDV-F010). Mora no
     *        pai: a variação não tem disponibilidade própria.
     * @param sessionProduct se o produto é vendido por <b>sessão de mesa</b>, não por unidade.
     * @param openRoshPrice preço do consumo livre, <b>do SKU pai</b>. É o campo que impede a
     *        armadilha do open rosh: a linha da comanda chega com o SKU da variação (para saber
     *        qual essência sair do estoque), mas o valor cobrado é este, e não
     *        {@code pricing.effectivePrice()}. Nulo quando o produto não oferece consumo livre.
     */
    record CatalogSaleInfo(String productName, Pricing pricing, boolean availableForTable,
            boolean sessionProduct, BigDecimal openRoshPrice, Integer sessionsPerUnit, boolean kit,
            boolean parentWithVariants) {

        /**
         * Forma sem {@code parentWithVariants} (PDV-F042) — compatibilidade: SKU tratado como
         * vendável por si, que é o comportamento anterior.
         */
        public CatalogSaleInfo(String productName, Pricing pricing, boolean availableForTable,
                boolean sessionProduct, BigDecimal openRoshPrice, Integer sessionsPerUnit, boolean kit) {
            this(productName, pricing, availableForTable, sessionProduct, openRoshPrice, sessionsPerUnit, kit, false);
        }

        /**
         * Forma curta, para quem só precisa do par nome/preço: resolve os campos de mesa para o
         * mesmo default da migration — disponível na mesa, não vendido por sessão, sem open rosh.
         */
        public CatalogSaleInfo(String productName, Pricing pricing) {
            this(productName, pricing, true, false, null, null, false, false);
        }

        /**
         * Forma sem os campos de lata (EST-F027) e sem {@code kit} (PDV-C020) — compatibilidade
         * com chamadores anteriores, mesmo idioma das sobrecargas de {@code ProductFilter}.
         */
        public CatalogSaleInfo(String productName, Pricing pricing, boolean availableForTable,
                boolean sessionProduct, BigDecimal openRoshPrice) {
            this(productName, pricing, availableForTable, sessionProduct, openRoshPrice, null, false, false);
        }

        /**
         * O SKU é consumido por lata aberta (EST-F027)? Exige as duas pontas: ser produto de
         * sessão <b>e</b> declarar quantas sessões saem de uma unidade. Sem {@code sessionsPerUnit}
         * o comportamento antigo continua valendo — baixa direta de unidade —, o que torna a
         * adoção da lata uma escolha por produto, não uma virada de chave para o catálogo inteiro.
         */
        public boolean consumesOpenPackage() {
            return sessionProduct && sessionsPerUnit != null && sessionsPerUnit > 0;
        }
    }

    /**
     * Registra o consumo de {@code quantity} sessões na lata aberta de {@code sku} (EST-F027),
     * abrindo uma se não houver.
     *
     * <p>É o que substitui a {@code SAIDA} de uma unidade por sessão. <b>A unidade sai do saldo na
     * ABERTURA da lata</b>, não a cada sessão nem na reposição: o saldo passa a significar "latas
     * lacradas na prateleira", que é o que o operador conta no balanço, e nenhum movimento é
     * inventado — a saída acontece no instante físico em que a lata deixa a prateleira.</p>
     *
     * <p>Quantidade fracionária é arredondada <b>para cima</b>: não existe meia lata aberta, e meia
     * sessão consome um uso inteiro.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.NotAPackagedSessionProductException
     *         se o SKU não for produto de sessão com {@code sessionsPerUnit} declarado.
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException se não
     *         houver saldo para abrir a lata.
     */
    OpenPackage consumeSession(String sku, String warehouseCode, BigDecimal quantity, String username);

    /**
     * Desfaz {@code quantity} sessões da lata aberta — cancelamento de comanda e remoção de linha
     * (EST-F027).
     *
     * <p><b>Não devolve unidade ao estoque</b>, ao contrário do {@code ENTRADA} que o cancelamento
     * fazia antes: a essência já foi queimada e não voltou para a prateleira. Devolver unidade
     * criaria saldo que fisicamente não existe. Se não houver lata aberta — porque foi reposta
     * entre o lançamento e o cancelamento —, não faz nada: recusar o cancelamento por causa disso
     * deixaria a mesa presa por um detalhe de contabilidade de lata.</p>
     */
    void releaseSession(String sku, String warehouseCode, BigDecimal quantity);

    /**
     * "Repor essência" (EST-F027): descarta a lata em uso e abre outra, baixando <b>uma</b>
     * unidade do saldo.
     *
     * <p>Existe porque a lata acaba antes do previsto, que é o caso comum. A sobra
     * ({@code uses < sessionsPerUnit} na lata fechada) fica registrada no histórico e <b>não</b>
     * vira ajuste de estoque: a unidade já saiu do saldo quando foi aberta, e transformar o resto
     * em perda criaria movimento para medir uma quantidade que ninguém mediu.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException se não
     *         houver saldo para abrir a lata nova — a antiga <b>não</b> é fechada nesse caso, e o
     *         atendente continua com o que tem na mão.
     */
    OpenPackage replaceOpenPackage(String sku, String warehouseCode, String username);

    /**
     * Cadastra uma lata que <b>já estava aberta</b> antes de o sistema saber dela (EST-F033), com
     * as sessões que ela ainda rende — o inventário inicial das essências da mesa.
     *
     * <p><b>Não baixa estoque</b>, ao contrário de abrir e repor: a lata saiu da prateleira antes,
     * e baixar agora tiraria do saldo uma segunda lata que continua lacrada. Daqui em diante ela
     * segue o ciclo normal — quando esgota, a próxima sessão abre outra com {@code SAIDA}.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.NotAPackagedSessionProductException
     *         se o SKU não for produto de sessão com {@code sessionsPerUnit} declarado.
     * @throws com.cernecommerce.core.domain.exception.estoque.OpenPackageAlreadyOpenException se
     *         já houver lata aberta do SKU no depósito.
     * @throws com.cernecommerce.core.domain.exception.estoque.InvalidOpenPackageUsesException se
     *         {@code usesRemaining} não estiver entre 1 e {@code sessionsPerUnit}.
     */
    OpenPackage registerOpenPackage(String sku, String warehouseCode, int usesRemaining, String username);

    /**
     * Liga {@code childSku} a uma embalagem (EST-F032): {@code parentSku} contém
     * {@code unitsPerParent} dele — o maço contém 20 cigarros, a carteira contém 10 maços. A partir
     * daí toda {@code SAIDA} do filho que não couber no disponível abre o pai sozinha, em cascata.
     * Redefinir substitui a ligação do filho (um filho tem um pai só).
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.InvalidPackagingException kit, produto
     *         base com variações, produto com lote, ciclo ou cadeia acima de 4 níveis
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException SKU desconhecido
     */
    SkuPackaging definePackaging(String childSku, String parentSku, int unitsPerParent);

    /**
     * Desliga {@code childSku} da embalagem (EST-F032). Saldos não mudam.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.PackagingNotFoundException sem ligação
     */
    void removePackaging(String childSku);

    /**
     * A cadeia de embalagem que passa por {@code sku}, da mais externa para a mais interna, com o
     * disponível de cada nível quando {@code warehouseCode} vem informado (EST-F032). SKU sem
     * ligação devolve só ele mesmo.
     */
    List<PackagingLevel> getPackagingChain(String sku, String warehouseCode);

    /**
     * Um nível da cadeia de embalagem (EST-F032).
     *
     * @param containsSku o nível de dentro; nulo no nível mais interno
     * @param containsUnits quantos do nível de dentro este contém; nulo no mais interno
     * @param available disponível no depósito pedido; nulo sem depósito
     */
    record PackagingLevel(String sku, String containsSku, Integer containsUnits, BigDecimal available) {
    }

    /**
     * A central de cigarros do PDV (PDV-F041): todo produto <b>ativo</b> com alguma embalagem ligada
     * (decisão do dono — não depende da categoria), cada um com as suas cadeias (uma por cor, da
     * carteira ao solto), e em cada nível o preço e o disponível no depósito. A venda continua sendo
     * a do balcão; a quebra de embalagem é de EST-F032.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException depósito desconhecido
     */
    List<PackagedFamily> listPackagedFamilies(String warehouseCode);

    /** Um produto da central de cigarros (PDV-F041) e as suas cadeias de embalagem. */
    record PackagedFamily(String productSku, String productName, List<PackagedLine> lines) {
    }

    /** Uma cadeia, da embalagem mais externa à mais interna — tipicamente uma cor. */
    record PackagedLine(List<PackagedLevel> levels) {
    }

    /**
     * Um nível vendável da cadeia.
     *
     * @param label os atributos da variação ("azul · maço"); o nome do produto para SKU sem variação
     * @param price o preço efetivo do SKU (o da variação, ou o do produto); nulo sem preço
     */
    record PackagedLevel(String sku, String label, BigDecimal price, BigDecimal available, String containsSku,
            Integer containsUnits) {
    }

    /** Latas em uso num depósito. */
    List<OpenPackage> listOpenPackages(String warehouseCode);

    /**
     * A lata em uso de um SKU.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.OpenPackageNotFoundException se não
     *         houver nenhuma aberta — distinto de "produto não usa lata", que é
     *         {@code NotAPackagedSessionProductException}.
     */
    OpenPackage findOpenPackage(String sku, String warehouseCode);

    /**
     * Resolve o produto (com categoria e precificação) de qualquer SKU do catálogo — pai ou
     * variação. Usado, por exemplo, pela cadeia de resolução de taxa de cashback (CRM-F003), que
     * precisa da categoria do produto, não só do preço.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException
     *         se o SKU não existir no catálogo.
     */
    Product findProductBySku(String sku);

    /**
     * Resumo pré-calculado do catálogo de estoque — números agregados que várias telas do admin
     * hoje recalculam do zero baixando o catálogo inteiro e cruzando com saldos e pontos de
     * reposição em memória (badge de alertas, tela de Alertas de Reposição, KPIs do Catálogo de
     * Produtos, painel de Estoque do Dashboard).
     */
    EstoqueSummary getSummary();

    /**
     * Resolve o produto pai a partir do código de barras do próprio pai ou de qualquer variação —
     * caminho de leitura por scanner do PDV.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.BarcodeNotFoundException se nenhum
     *         produto tiver esse código de barras.
     */
    Product findProductByBarcode(String barcode);

    /**
     * Ativa ou desativa um produto (EST-F018). Produto inativo <b>recusa entrada</b> de estoque
     * — manual ou por recebimento de Compras —, mas continua aceitando saída, para escoar o saldo
     * remanescente. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * pai não existir.
     */
    Product setProductActive(String sku, boolean active);

    /**
     * EST-F036 — liga ou desliga a venda do SKU <b>base</b> de um produto com variações. Desligado
     * (o padrão), a base não se vende nem recebe entrada de estoque; as variações não mudam. Sem
     * variações não tem efeito. Só aceita o SKU pai.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException SKU pai desconhecido
     */
    Product setParentSellable(String sku, boolean parentSellable);

    /**
     * Ativa ou desativa o rastreamento de lote e validade de um produto (EST-F008) — opt-in por
     * SKU, já que só essência/carvão/perecível faz sentido rastrear. A partir daqui,
     * {@link #adjustStock(String, String, MovementType, BigDecimal, String, String, String, LocalDate)}
     * passa a exigir lote em toda {@code ENTRADA} deste SKU. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * pai não existir, ou {@link IllegalArgumentException} se o SKU for um {@code KIT} — kit não
     * tem saldo físico próprio para rastrear.
     */
    Product setProductLotTracked(String sku, boolean lotTracked);

    /**
     * Alteração parcial de depósito (EST-F018): {@code name} e/ou {@code type} nulos são mantidos.
     * Não altera {@code code}. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}.
     */
    Warehouse updateWarehouse(String code, String name, WarehouseType type);

    /**
     * Ativa ou desativa um depósito (EST-F018). Mesma regra do produto: para de receber entrada,
     * continua despachando saída.
     */
    Warehouse setWarehouseActive(String code, boolean active);

    /**
     * Cria um depósito (loja física ou e-commerce). Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException}
     * se o código já existir.
     */
    Warehouse createWarehouse(String code, String name, WarehouseType type);

    /** Lista todos os depósitos cadastrados. */
    /** Lista depósitos paginados, ordenados por id. */
    PageResult<Warehouse> listWarehouses(int page, int size);

    /**
     * Busca um depósito por id. Existe para o adapter traduzir o {@code warehouseId} que os
     * modelos guardam no {@code warehouseCode} que a API expõe — caso do balanço de inventário,
     * consultado por id e sem código na requisição. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}.
     */
    Warehouse getWarehouse(Long warehouseId);

    /**
     * Busca um depósito por código. Existe para quem precisa <b>validar</b> um código antes de
     * guardá-lo — caso da abertura de caixa (PDV-F001), que carimba o depósito na sessão e não pode
     * descobrir na primeira venda que ele não existe. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}.
     */
    Warehouse getWarehouseByCode(String code);

    /**
     * Depósito padrão do marketplace (ECM-F002/F003, plano §2.2): a operação de hoje tem um
     * depósito físico só, e a superfície pública (catálogo, e futuramente checkout) não tem
     * sessão de operador para informar um {@code warehouseCode} explícito. Resolvido a partir de
     * {@code system_config.estoque.warehouse.default-code}.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException
     *         se a chave não estiver configurada (ausente ou em branco)
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se o
     *         código configurado não corresponder a nenhum depósito existente
     */
    Warehouse getDefaultWarehouse();

    /**
     * Consulta o saldo de um SKU em um depósito. Retorna saldo zero se ainda não houve
     * nenhuma movimentação para o par SKU/depósito. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir.
     */
    StockBalance getStockBalance(String sku, String warehouseCode);

    /**
     * Saldo paginado de todos os produtos de um depósito, ordenado por SKU — alimenta telas
     * de alertas de reposição que precisam comparar o saldo de cada produto contra o seu ponto
     * de reposição. Não inclui KITs: eles não têm linha própria em {@code stock_balance} (o
     * saldo é derivado ao vivo só na consulta por 1 SKU, ver {@link #getStockBalance}). Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir.
     */
    PageResult<StockBalance> listStockBalances(String warehouseCode, int page, int size);

    /**
     * Registra uma movimentação manual de estoque (entrada, saída ou ajuste) e atualiza o
     * {@link StockBalance} correspondente na mesma transação. Retorna o saldo atualizado.
     * Lança {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o
     * SKU não existir no catálogo (nem como SKU pai, nem como SKU de variação),
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.InsufficientStockException} se
     * uma SAIDA deixaria o saldo negativo.
     */
    StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username);

    /**
     * Converte saldo de um SKU em saldo de outro <b>numa única transação</b> (EST-F025): uma
     * {@code SAIDA} de {@code fromQuantity} em {@code fromSku} e uma {@code ENTRADA} de
     * {@code toQuantity} em {@code toSku}, no mesmo depósito.
     *
     * <p><b>Por que não bastam dois {@code adjustStock}.</b> É assim que a operação é feita hoje —
     * dois {@code POST /estoque/movements} disparados em sequência pelo cliente —, e cada um é sua
     * própria transação: se o segundo falhar (conflito de {@code @Version}, rede, permissão), a lata
     * saiu do saldo e nenhuma sessão entrou, sem compensação nem rastro de que os dois movimentos
     * eram um ato só. Aqui ou os dois acontecem, ou nenhum.</p>
     *
     * <p>O caso que a motivou é o lounge: uma lata de essência vira N sessões de narguilé. Lata e
     * sessão são SKUs distintos por decisão da V112 (toda quantidade do sistema é inteira), e
     * {@code sessionsPerUnit} é sugestão de tela — por isso {@code toQuantity} é <b>explícito</b>
     * aqui, e não derivado: o saldo não pode depender de um número que o admin edita no catálogo.</p>
     *
     * <p>Não há {@code MovementType} novo: são uma {@code SAIDA} e uma {@code ENTRADA} comuns, com
     * {@code reason} cruzado para o ledger mostrar o vínculo. Acrescentar um {@code TRANSFER} ao enum
     * mexeria no {@code CHECK} de {@code stock_movement} e na semântica de {@code AJUSTE} sem
     * entregar nada que a transação já não entregue.</p>
     *
     * @param reason motivo livre, gravado nas duas pontas do ledger junto do SKU do outro lado
     * @throws com.cernecommerce.core.domain.exception.estoque.SameSkuConversionException se origem e
     *         destino forem o mesmo SKU
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException se o saldo
     *         de {@code fromSku} não cobrir a saída — e então a entrada <b>não</b> acontece
     * @throws com.cernecommerce.core.domain.exception.estoque.KitDirectAdjustmentException se
     *         qualquer um dos lados for um SKU de kit, que não tem saldo próprio
     */
    StockConversionResult convertStock(String fromSku, String toSku, BigDecimal fromQuantity,
            BigDecimal toQuantity, String warehouseCode, String reason, String username);

    /**
     * Os dois saldos depois de uma conversão — quanto sobrou da origem e quanto passou a existir do
     * destino. Devolver os dois evita o {@code GET} extra que a tela faria para mostrar o resultado.
     */
    record StockConversionResult(StockBalance from, StockBalance to) {
    }

    /**
     * Curva ABC e giro do consumo de um período (EST-F011), da maior participação para a menor.
     *
     * <p>Classifica os SKUs por <b>valor consumido</b> (quantidade de saída × custo médio) na regra
     * de Pareto — 80% em A, até 95% em B, o resto em C — e devolve o giro de cada um. É o relatório
     * que responde "onde meu dinheiro está parado" para quem faz a compra.</p>
     *
     * <p>A fonte é o ledger de {@code SAIDA}, não as vendas: o que precisa ser reposto é tudo que
     * saiu da prateleira, e cortesia, perda e conversão saem sem virar venda.</p>
     *
     * @param warehouseCode depósito a filtrar; nulo agrega a loja inteira
     */
    List<AbcAnalysis.AbcEntry> findAbcAnalysis(String warehouseCode, Instant from, Instant to);

    /**
     * Mesmo que {@link #adjustStock(String, String, MovementType, BigDecimal, String, String)}, com
     * lote e validade explícitos (EST-F008) — só se aplica a {@code ENTRADA} de SKU
     * {@link com.cernecommerce.core.domain.model.estoque.Product#lotTracked()}.
     * {@code SAIDA} não recebe lote: o consumo é automático por FEFO (do que vence primeiro em
     * diante), entre os lotes já recebidos.
     *
     * @param lotCode identificador do lote recebido; obrigatório junto com {@code expiryDate}
     *        quando o SKU é lote-rastreado e o tipo é {@code ENTRADA}.
     * @param expiryDate validade do lote. Se o {@code lotCode} já existir para o par SKU/depósito
     *        com outra validade gravada, a chamada falha — o mesmo lote não muda de validade.
     * @throws com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException se o SKU é
     *         lote-rastreado, o tipo é {@code ENTRADA} e {@code lotCode}/{@code expiryDate} não
     *         vierem preenchidos
     * @throws com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException se
     *         {@code lotCode}/{@code expiryDate} vierem preenchidos para um SKU não lote-rastreado,
     *         ou para um tipo diferente de {@code ENTRADA}
     * @throws com.cernecommerce.core.domain.exception.estoque.LotExpiryDateMismatchException se o
     *         {@code lotCode} já existir com outra validade
     */
    StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode, LocalDate expiryDate);

    /**
     * Mesmo que {@link #adjustStock(String, String, MovementType, BigDecimal, String, String, String, LocalDate)},
     * com custo unitário de entrada explícito (EST-F007) — alimenta o recálculo do custo médio
     * ponderado ({@link StockBalance#averageCost()}) do par SKU/depósito.
     *
     * @param unitCost custo unitário da entrada, opcional mesmo em {@code ENTRADA} — nem toda
     *        entrada tem custo conhecido no momento (ex.: balanço de inventário nunca tem). Ausência
     *        não é erro, só não atualiza o custo médio daquela vez.
     * @throws com.cernecommerce.core.domain.exception.estoque.UnexpectedUnitCostException se
     *         {@code unitCost} vier preenchido para um tipo diferente de {@code ENTRADA}, ou para um
     *         SKU que é kit (kit não tem saldo próprio, não acumula custo médio)
     */
    default StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode, LocalDate expiryDate, BigDecimal unitCost) {
        return adjustStock(sku, warehouseCode, type, quantity, reason, username, lotCode, expiryDate, unitCost, null);
    }

    /**
     * Mesmo que {@link #adjustStock(String, String, MovementType, BigDecimal, String, String, String, LocalDate, BigDecimal)},
     * com o vínculo opcional do recebimento de mercadoria que originou a movimentação (item 2 do
     * pedido do frontend — histórico de compras por SKU). Único método abstrato de ajuste de
     * estoque — todas as sobrecargas acima delegam até aqui. Sobrecarga aditiva: nenhum chamador
     * pré-existente precisa mudar, só {@code ComprasService.receiveGoods} passa a usar esta forma.
     *
     * @param goodsReceiptId id do {@code GoodsReceipt} que originou esta {@code ENTRADA}, ou
     *        {@code null} para movimentação manual (sem recebimento associado).
     */
    StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode, LocalDate expiryDate, BigDecimal unitCost,
            Long goodsReceiptId);

    /**
     * Lista os lotes de um SKU num depósito (EST-F008), do que vence primeiro em diante. Lista
     * vazia se o SKU não é lote-rastreado ou ainda não recebeu nenhum lote — não é erro, é o
     * estado normal de todo SKU antes do primeiro {@code adjustStock(ENTRADA)} com lote.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se o
     *         código do depósito não existir
     */
    List<StockLot> listStockLots(String sku, String warehouseCode);

    /**
     * Histórico paginado de movimentações, mais recentes primeiro. {@code sku} e
     * {@code warehouseCode} são opcionais — omitidos, o feed traz todas as movimentações; quando
     * informados, filtram por esse SKU e/ou depósito. Retorna página vazia se o filtro nunca foi
     * movimentado. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito informado não existir.
     */
    default PageResult<StockMovement> listMovements(String sku, String warehouseCode, int page, int size) {
        return listMovements(sku, warehouseCode, null, null, null, page, size);
    }

    /**
     * Mesmo que {@link #listMovements(String, String, int, int)}, com filtro adicional de
     * {@code type} e intervalo {@code [from, to]} de data (item 6 do pedido do frontend) — todos
     * opcionais, {@code null} não filtra por esse critério.
     */
    PageResult<StockMovement> listMovements(String sku, String warehouseCode, MovementType type, Instant from,
            Instant to, int page, int size);

    /**
     * Histórico paginado de {@code ENTRADA}s de um SKU num depósito, mais recentes primeiro (item
     * 2 do pedido do frontend — referência de última compra). Substitui a varredura client-side de
     * {@code GET /estoque/movements?sku=...&size=20} que perdia a referência em SKUs de giro
     * rápido com mais de 20 movimentações desde a última entrada.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se o
     *         código do depósito não existir
     */
    PageResult<StockMovement> listPurchaseHistory(String sku, String warehouseCode, int page, int size);

    /**
     * Define (cria ou atualiza) o ponto de reposição de um SKU em um depósito. A partir dessa
     * chamada, toda movimentação que deixe o saldo abaixo de {@code minQuantity} dispara uma
     * notificação para os usuários com permissão {@code ESTOQUE_STOCK_MANAGE}. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * não existir no catálogo, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir.
     */
    void setReorderPoint(String sku, String warehouseCode, BigDecimal minQuantity);

    /**
     * Remove o ponto de reposição de um SKU num depósito, se existir. Idempotente: não é erro
     * remover o que não existe — a variante de estoque pode simplesmente nunca ter tido um
     * mínimo próprio configurado. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir.
     */
    void deleteReorderPoint(String sku, String warehouseCode);

    /**
     * Consulta o ponto de reposição de um SKU num depósito. Vazio se não houver nenhum
     * configurado — leitura não exige que o SKU já exista no catálogo (mesma leniência de
     * {@link #getStockBalance}). Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir.
     */
    Optional<ReorderPoint> getReorderPoint(String sku, String warehouseCode);

    /**
     * Pontos de reposição configurados num depósito, paginados, ordenados por SKU — alimenta
     * telas de alertas de reposição junto com {@link #listStockBalances}. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir.
     */
    PageResult<ReorderPoint> listReorderPoints(String warehouseCode, int page, int size);

    /**
     * Diagnóstico de integridade (EST-C011): pares SKU/depósito com saldo, movimentação ou ponto
     * de reposição gravados cujo SKU não existe no catálogo, nem como SKU pai nem como SKU de
     * variação.
     *
     * <p>Desde EST-C002 nenhuma escrita nova cria um órfão — {@code adjustStock} e
     * {@code setReorderPoint} barram SKU desconhecido. O que esta consulta levanta é o passivo
     * anterior àquela correção, que continua na base porque não há FK das tabelas de estoque
     * para {@code product}.</p>
     *
     * <p><b>Somente leitura, e de propósito:</b> o destino de cada órfão — cadastrar o produto
     * que falta ou expurgar a linha — é decisão humana. Expurgar em massa arriscaria apagar
     * histórico legítimo, então não existe operação de limpeza automática.</p>
     *
     * <p>Ordenado por {@code sku, warehouseCode}. Página vazia quando a base está íntegra.</p>
     */
    PageResult<OrphanSku> listOrphanSkus(int page, int size);

    /**
     * Diagnóstico de integridade da reserva (EST-C013): pares SKU/depósito cujo
     * {@code stock_balance.reserved_quantity} diverge da soma das reservas {@code ACTIVE} no
     * ledger {@code stock_reservation}.
     *
     * <p>Diferente do órfão de SKU, essa divergência é <b>estoque travado invisível</b>: a venda
     * recusa por reserva e não há reserva ativa que a explique (ou o inverso). Somente leitura —
     * a correção é decisão humana.</p>
     *
     * <p>Ordenado por {@code sku, warehouseCode}. Página vazia quando a base está íntegra.</p>
     */
    PageResult<ReservationIntegrityMismatch> listReservationMismatches(int page, int size);

    /**
     * Diagnóstico de integridade de lote (EST-F008): pares SKU/depósito de SKU lote-rastreado cujo
     * {@code stock_balance.quantity} diverge da soma de {@code stock_lot.quantity} para o mesmo
     * par.
     *
     * <p>{@code stock_lot} é aditivo (ver javadoc de {@code StockLot}) — mantido pela mesma
     * transação de {@code adjustStock}, mas sem FK que force a igualdade. O caminho conhecido de
     * drift é {@code consumeLotsFefo} não achar saldo suficiente nos lotes para cobrir uma SAIDA já
     * validada contra o agregado (comentário em {@code EstoqueService}) — aqui é onde esse drift
     * fica visível, em vez de silencioso. Somente leitura — a correção é decisão humana.</p>
     *
     * <p>Ordenado por {@code sku, warehouseCode}. Página vazia quando a base está íntegra.</p>
     */
    PageResult<LotIntegrityMismatch> listLotMismatches(int page, int size);

    // ---------------------------------------------------------------------------------------
    // Balanço de inventário (EST-F006)
    // ---------------------------------------------------------------------------------------

    /**
     * Abre um balanço para o depósito. Só pode haver <b>um aberto por depósito</b>: dois
     * simultâneos sobre o mesmo saldo se sobrescreveriam no fechamento. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} ou
     * {@link IllegalStateException} se já houver um aberto.
     */
    StockCount openStockCount(String warehouseCode, String username);

    /**
     * Registra o que foi contado de um SKU não lote-rastreado. É upsert por SKU — recontar
     * sobrescreve. Exige balanço {@link com.cernecommerce.core.domain.model.estoque.StockCountStatus#ABERTA}
     * e SKU existente no catálogo.
     *
     * <p>Equivale a {@link #recordCountedItem(Long, String, BigDecimal, String)} com
     * {@code lotCode = null} — falha se o SKU for lote-rastreado (EST-F008), porque nesse caso a
     * contagem precisa dizer qual lote.</p>
     */
    StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity);

    /**
     * Mesmo que {@link #recordCountedItem(Long, String, BigDecimal)}, com o lote contado explícito
     * (EST-F008). SKU lote-rastreado exige {@code lotCode}; SKU não lote-rastreado recusa se vier
     * preenchido. É upsert por {@code (sku, lotCode)} — recontar o mesmo lote sobrescreve, contar
     * um lote diferente do mesmo SKU acrescenta uma linha.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException se o SKU for
     *         lote-rastreado e {@code lotCode} vier nulo/vazio
     * @throws com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException se o SKU
     *         não for lote-rastreado e {@code lotCode} vier preenchido
     * @throws com.cernecommerce.core.domain.exception.estoque.StockLotNotFoundException se o
     *         {@code lotCode} não corresponder a nenhum lote existente — a contagem só reconcilia
     *         lote que já existe; lote novo entra por recebimento, não pelo balanço
     */
    StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity, String lotCode);

    /**
     * Fecha o balanço e aplica os ajustes: para cada item cuja contagem <b>divirja</b> do saldo do
     * sistema, grava um {@link MovementType#AJUSTE} levando o saldo ao valor contado. Item que
     * bateu não gera movimentação — contagem certa não polui o ledger.
     *
     * <p>SKU lote-rastreado (EST-F008) é reconciliado por lote primeiro — cada {@code StockLot}
     * contado recebe seu próprio {@code reconciledTo}, para {@code SUM(stock_lot.quantity)}
     * continuar igual a {@code stock_balance.quantity} — e só então o agregado recebe UM
     * {@code AJUSTE} para a soma dos lotes, no mesmo formato de ledger do SKU não lote-rastreado.</p>
     *
     * <p>Tudo na mesma transação: se um SKU falhar, nenhum ajuste é aplicado e o balanço continua
     * aberto. Os itens ficam com {@code expectedQuantity} e {@code difference} carimbados, que é o
     * registro auditável da divergência.</p>
     */
    StockCount closeStockCount(Long stockCountId, String username);

    /** Abandona o balanço sem tocar em saldo nenhum. Exige que esteja aberto. */
    StockCount cancelStockCount(Long stockCountId);

    /** Consulta um balanço com seus itens. */
    StockCount getStockCount(Long stockCountId);

    /** Balanços de um depósito, dos mais recentes para os mais antigos. */
    PageResult<StockCount> listStockCounts(String warehouseCode, int page, int size);

    // ---------------------------------------------------------------------------------------
    // Reserva de estoque (EST-F021)
    // ---------------------------------------------------------------------------------------

    /**
     * Compromete saldo de um SKU sem tirá-lo da prateleira, para que balcão e marketplace consumam
     * o mesmo depósito sem overselling. É o caminho do <b>checkout online</b>: entre montar o
     * pedido e o pagamento confirmar, o saldo precisa estar prometido sem ter saído.
     *
     * <p>O PDV <b>não</b> usa este caminho — no balcão a mercadoria sai na hora, e
     * {@link #adjustStock} com {@code SAIDA} continua sendo o correto.</p>
     *
     * @param ownerReference identificador de quem pediu a reserva, usado depois para consumir ou
     *        liberar o conjunto todo. Obrigatório.
     * @param ttl validade da reserva; {@code null} usa o padrão configurado. Reserva sem prazo
     *        seria saldo perdido sem ninguém perceber, então não há como criar uma perpétua.
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU não
     *         existir no catálogo
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se o
     *         depósito não existir
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException se o
     *         <b>disponível</b> não cobrir a quantidade
     */
    StockReservation reserveStock(String sku, String warehouseCode, BigDecimal quantity,
            String ownerReference, Duration ttl, String username);

    /**
     * Converte a reserva em saída de verdade: grava um {@link MovementType#SAIDA} no ledger e baixa
     * o físico e o reservado juntos. O disponível não se mexe — já estava descontado desde a
     * reserva. É o que o webhook de pagamento confirmado chama.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException
     *         se a reserva não existir
     * @throws com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException
     *         se já tiver sido consumida, liberada ou expirada — consumir duas vezes daria baixa
     *         dobrada na mesma mercadoria
     */
    StockReservation consumeReservation(Long reservationId, String username);

    /**
     * Devolve a reserva ao disponível sem mexer no físico — pedido cancelado ou carrinho desfeito.
     * Não gera movimentação: nada entrou nem saiu da prateleira.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException
     *         se a reserva não existir
     * @throws com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException
     *         se já estiver resolvida
     */
    StockReservation releaseReservation(Long reservationId, String username);

    /**
     * Libera de uma vez todas as reservas ativas de um dono. É a operação que o cancelamento de um
     * pedido com vários itens usa, para não depender de o chamador ter guardado id por id.
     *
     * @return quantas reservas foram liberadas; zero se não havia nenhuma ativa (idempotente de
     *         propósito — cancelar um pedido duas vezes não é erro)
     */
    int releaseReservationsByOwner(String ownerReference, String username);

    /**
     * Converte de uma vez todas as reservas ativas de um dono em saída real — o gêmeo de
     * {@link #releaseReservationsByOwner} para o caminho feliz. É o que a confirmação de pagamento
     * de um pedido com vários itens usa, inclusive quando esse pagamento acontece no balcão.
     *
     * <p>O físico e o reservado caem juntos; o disponível não se mexe, porque já estava descontado
     * desde a reserva.</p>
     *
     * @return quantas reservas foram consumidas; zero se não havia nenhuma ativa
     */
    int consumeReservationsByOwner(String ownerReference, String username);

    /** Consulta uma reserva. */
    StockReservation getStockReservation(Long reservationId);

    /**
     * Listagem filtrada de reservas, mais recentes primeiro. {@code sku}, {@code warehouseCode} e
     * {@code status} são opcionais e se combinam.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se
     *         {@code warehouseCode} for informado e não existir
     */
    PageResult<StockReservation> listReservations(String sku, String warehouseCode, ReservationStatus status,
            int page, int size);

    /**
     * Expira as reservas vencidas, devolvendo a quantidade ao disponível. Chamado pelo varredor
     * agendado, em lotes.
     *
     * @return quantas reservas foram expiradas nesta passada
     */
    int expireReservations(int batchSize);

    /**
     * Notifica quem tem {@code ESTOQUE_STOCK_MANAGE} sobre todo {@link
     * com.cernecommerce.core.domain.model.estoque.StockLot} que vence em {@code cutoffDays} dias
     * ou menos e ainda não foi alertado (EST-F008). Chamado pelo varredor agendado, em lotes —
     * cada lote alertado é carimbado ({@code StockLot#alerted()}) para o próximo run não repetir.
     *
     * @return quantos lotes foram alertados nesta passada
     */
    int alertExpiringLots(int cutoffDays, int batchSize);

    // ---------------------------------------------------------------------------------------
    // Kits (EST-F015) — virtuais, de um nível só (§2.10 do plano)
    // ---------------------------------------------------------------------------------------

    /** O que o chamador informa por linha de receita: SKU do componente e quantidade consumida. */
    record KitComponentCommand(String componentSku, BigDecimal quantity) {
    }

    /**
     * Define (substitui integralmente) a receita de um kit e promove o produto a {@code KIT}
     * como efeito colateral — não é preciso chamar nada além disto para um SKU virar kit.
     *
     * <p>PUT idempotente: chamar de novo com uma lista diferente substitui a receita inteira, não
     * mescla. Componente precisa ser {@code SIMPLES} — kit dentro de kit é proibido por
     * construção, não detectado por travessia.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se
     *         {@code kitSku} não existir como SKU pai, ou se algum {@code componentSku} não
     *         existir no catálogo
     * @throws com.cernecommerce.core.domain.exception.estoque.EmptyKitRecipeException se
     *         {@code components} vier vazio
     * @throws com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException se o
     *         produto alvo já tiver variações cadastradas
     * @throws com.cernecommerce.core.domain.exception.estoque.KitComponentAlreadyInUseException
     *         se {@code kitSku} já for componente de outro kit
     * @throws com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException se algum
     *         componente for o próprio {@code kitSku}
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateKitComponentException se
     *         o mesmo {@code componentSku} aparecer mais de uma vez na lista
     * @throws com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException se
     *         algum componente não for {@code SIMPLES}
     * @throws com.cernecommerce.core.domain.exception.estoque.KitComponentInactiveException se
     *         algum componente estiver com {@code active = false}
     */
    Product defineKitRecipe(String kitSku, List<KitComponentCommand> components);

    /**
     * Receita vigente de um kit. Lista vazia se o SKU existe mas nunca foi promovido a kit.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir no catálogo
     */
    List<KitComponent> getKitRecipe(String kitSku);

    /**
     * Mesma receita de {@link #getKitRecipe}, enriquecida com dados de catálogo de cada componente
     * (nome, imagem, preço de venda, se está ativo) — para a tela de edição de receita não
     * precisar de N chamadas extras nem de {@code warehouseCode} (para dados de saldo, ver
     * {@link #getKitAvailability}).
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir no catálogo
     */
    List<KitComponentDetail> getKitRecipeDetailed(String kitSku);

    /**
     * Remove a receita de um kit e rebaixa o produto para {@code SIMPLES} (Bloco 3.2) — decisão
     * consciente: kit sem receita fica inutilizável em qualquer venda
     * ({@code EmptyKitRecipeException}), então "esvaziar" e "deixar de ser kit" são a mesma
     * operação neste domínio. Idempotente: chamar num produto que já é {@code SIMPLES} não faz
     * nada e devolve o produto como está.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir no catálogo
     */
    Product clearKitRecipe(String kitSku);

    /**
     * Disponibilidade de montagem de todos os kits de um depósito — resolve no servidor o que o
     * frontend hoje deriva com {@code N+1} chamadas (catálogo de kits + catálogo de componentes +
     * saldos + uma consulta de receita por kit). {@code blocked} filtra só os kits travados
     * ({@code buildableQuantity == 0}) quando informado.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se
     *         {@code warehouseCode} não existir
     */
    PageResult<KitAvailability> getKitAvailability(String warehouseCode, Boolean blocked, int page, int size);
}
