package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.ports.out.event.AuditEventPublisherPort;
import com.cernecommerce.core.domain.exception.estoque.BarcodeNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.BrandHasProductsException;
import com.cernecommerce.core.domain.exception.estoque.BrandNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.CategoryHasProductsException;
import com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateAttributeTypeNameException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateBrandNameException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateCategoryNameException;
import com.cernecommerce.core.domain.exception.estoque.ReplenishmentItemNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateKitComponentException;
import com.cernecommerce.core.domain.exception.estoque.DraftLimitReachedException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.EmptyKitRecipeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentAlreadyInUseException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentInactiveException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentNotEligibleException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException;
import com.cernecommerce.core.domain.exception.estoque.KitCostNotEditableException;
import com.cernecommerce.core.domain.exception.estoque.KitDirectAdjustmentException;
import com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException;
import com.cernecommerce.core.domain.exception.estoque.KitInitialStockNotAllowedException;
import com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException;
import com.cernecommerce.core.domain.exception.estoque.LotExpiryDateMismatchException;
import com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.SameSkuConversionException;
import com.cernecommerce.core.domain.exception.estoque.ProductVariantNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockLotNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedUnitCostException;
import com.cernecommerce.core.domain.exception.estoque.NotAPackagedSessionProductException;
import com.cernecommerce.core.domain.exception.estoque.OpenPackageNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.OpenPackageAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.InvalidPackagingException;
import com.cernecommerce.core.domain.exception.estoque.PackagingNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ParentNotSellableException;
import com.cernecommerce.core.domain.model.estoque.SkuPackaging;
import com.cernecommerce.core.ports.out.estoque.SkuPackagingRepository;
import com.cernecommerce.core.domain.exception.estoque.ProductHasStockHistoryException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotDraftException;
import com.cernecommerce.core.domain.exception.estoque.VariantHasStockHistoryException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.AbcAnalysis;
import com.cernecommerce.core.domain.model.estoque.AttributeType;
import com.cernecommerce.core.domain.model.estoque.Brand;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.EstoqueSummary;
import com.cernecommerce.core.domain.model.estoque.KitAvailability;
import com.cernecommerce.core.domain.model.estoque.KitBlockedAlert;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.KitComponentAvailability;
import com.cernecommerce.core.domain.model.estoque.KitComponentDetail;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.OpenPackageCloseReason;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderAlert;
import com.cernecommerce.core.domain.model.estoque.ReorderAlertCounts;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.estoque.AttributeTypeRepository;
import com.cernecommerce.core.ports.out.estoque.BrandRepository;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import com.cernecommerce.core.ports.out.estoque.KitComponentRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ReorderPointRepository;
import com.cernecommerce.core.ports.out.estoque.ReplenishmentListRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockCountRepository;
import com.cernecommerce.core.ports.out.estoque.StockIntegrityRepository;
import com.cernecommerce.core.ports.out.estoque.StockLotRepository;
import com.cernecommerce.core.ports.out.estoque.OpenPackageRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.StockReservationRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.notification.ManagerNotificationPort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class EstoqueService implements EstoqueUseCase {

    private static final Logger log = LoggerFactory.getLogger(EstoqueService.class);

    private static final String STOCK_MANAGE_PERMISSION = "ESTOQUE_STOCK_MANAGE";
    private static final String REORDER_ALERT_BATCH = "estoque.reorder-alerts";
    private static final String KIT_BLOCKED_ALERT_BATCH = "estoque.kit-blocked-alerts";
    private static final String DEFAULT_WAREHOUSE_CONFIG_KEY = "estoque.warehouse.default-code";

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReorderPointRepository reorderPointRepository;
    private final StockIntegrityRepository stockIntegrityRepository;
    private final StockCountRepository stockCountRepository;
    private final StockReservationRepository stockReservationRepository;
    private final NotificationUseCase notificationUseCase;
    private final UserRepository userRepository;
    private final AfterCommitExecutor afterCommitExecutor;
    private final Duration defaultReservationTtl;
    private final KitComponentRepository kitComponentRepository;
    private final StockLotRepository stockLotRepository;
    private final SystemConfigPort systemConfigPort;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final AttributeTypeRepository attributeTypeRepository;
    private final ReplenishmentListRepository replenishmentListRepository;
    private final OpenPackageRepository openPackageRepository;
    private final ManagerNotificationPort managerNotifications;

    private final AuditEventPublisherPort auditEvents;
    private final SkuPackagingRepository skuPackagingRepository;

    public EstoqueService(ProductRepository productRepository, WarehouseRepository warehouseRepository,
            StockBalanceRepository stockBalanceRepository, StockMovementRepository stockMovementRepository,
            ReorderPointRepository reorderPointRepository, StockIntegrityRepository stockIntegrityRepository,
            StockCountRepository stockCountRepository, StockReservationRepository stockReservationRepository,
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            AfterCommitExecutor afterCommitExecutor, Duration defaultReservationTtl,
            KitComponentRepository kitComponentRepository, StockLotRepository stockLotRepository,
            SystemConfigPort systemConfigPort, CategoryRepository categoryRepository,
            BrandRepository brandRepository, AttributeTypeRepository attributeTypeRepository,
            ReplenishmentListRepository replenishmentListRepository,
            OpenPackageRepository openPackageRepository, ManagerNotificationPort managerNotifications,
            AuditEventPublisherPort auditEvents, SkuPackagingRepository skuPackagingRepository) {
        this.stockReservationRepository = stockReservationRepository;
        this.defaultReservationTtl = defaultReservationTtl;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.reorderPointRepository = reorderPointRepository;
        this.stockIntegrityRepository = stockIntegrityRepository;
        this.stockCountRepository = stockCountRepository;
        this.notificationUseCase = notificationUseCase;
        this.userRepository = userRepository;
        this.afterCommitExecutor = afterCommitExecutor;
        this.kitComponentRepository = kitComponentRepository;
        this.stockLotRepository = stockLotRepository;
        this.systemConfigPort = systemConfigPort;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.attributeTypeRepository = attributeTypeRepository;
        this.replenishmentListRepository = replenishmentListRepository;
        this.openPackageRepository = openPackageRepository;
        this.auditEvents = auditEvents;
        this.managerNotifications = managerNotifications;
        this.skuPackagingRepository = skuPackagingRepository;
    }

    @Override
    @Transactional
    public Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale, boolean superPromo, String description,
            String videoUrl, List<String> images, List<ProductAttribute> attributes, Long categoryId,
            String barcode, MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible,
            Boolean visibleInPos, Boolean visibleInMarketplace, ProductType type, InitialStockCommand initialStock,
            String actorUsername, List<KitComponentCommand> kitComponents, ProductStatus status, Long brandId,
            TableSessionCommand tableSession) {
        TableSessionCommand mesa = TableSessionCommand.orEmpty(tableSession);
        List<ProductVariant> safeVariants = variants == null ? List.of() : variants;
        ProductType resolvedType = type == null ? ProductType.SIMPLES : type;
        ProductStatus resolvedStatus = status == null ? ProductStatus.ATIVO : status;
        // EST-F023: teto de 5 rascunhos, validado no servidor — o frontend também valida, mas em
        // memória, e duas abas do mesmo operador furariam o limite sem esta checagem aqui.
        if (resolvedStatus == ProductStatus.RASCUNHO && productRepository.countByStatus(ProductStatus.RASCUNHO) >= 5) {
            throw new DraftLimitReachedException();
        }
        // Kit não tem grade nem saldo próprio (EST-F015) — checado ANTES de qualquer escrita, na
        // mesma ordem tanto faça o kit nascer assim ou ser promovido depois via defineKitRecipe,
        // que já lança esta mesma exceção para variants não vazio.
        if (resolvedType == ProductType.KIT && !safeVariants.isEmpty()) {
            throw new KitHasVariantsException(sku);
        }
        if (resolvedType == ProductType.KIT && initialStock != null) {
            throw new KitInitialStockNotAllowedException(sku);
        }
        // Criação atômica de kit (Bloco 3.1): valida a receita ANTES de qualquer escrita, com as
        // mesmas regras de defineKitRecipe — inclusive o componente inativo (Bloco 4). Ausente ou
        // vazia preserva o comportamento anterior: kit nasce sem receita, precisa de PUT .../kit
        // depois. Um kit recém-criado nunca tem variações nem pode já ser componente de outro (o
        // SKU não existia até agora), então só o laço por componente se aplica aqui — as
        // invariantes do próprio kit (KitHasVariantsException/KitComponentAlreadyInUseException)
        // já são cobertas acima e não fazem sentido nesse contexto.
        List<KitComponent> recipe = resolvedType == ProductType.KIT && kitComponents != null
                && !kitComponents.isEmpty() ? validateAndBuildComponents(sku, kitComponents) : null;
        // O SKU pai e os das variações compartilham o mesmo espaço de nomes: uk_product_sku e
        // uk_product_variant_sku. Checar os dois aqui evita que a violação de constraint escape
        // como DataIntegrityViolationException, que o contrato do use case não prevê.
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(sku);
        for (ProductVariant variant : safeVariants) {
            if (!candidates.add(variant.sku())) {
                throw new DuplicateSkuException(variant.sku());
            }
        }
        for (String candidate : candidates) {
            if (productRepository.existsBySku(candidate)) {
                throw new DuplicateSkuException(candidate);
            }
        }
        // Mesmo raciocínio acima, para o espaço de nomes de código de barras (pai + variações).
        Set<String> barcodeCandidates = new LinkedHashSet<>();
        if (barcode != null) {
            barcodeCandidates.add(barcode);
        }
        for (ProductVariant variant : safeVariants) {
            if (variant.barcode() != null && !barcodeCandidates.add(variant.barcode())) {
                throw new DuplicateBarcodeException(variant.barcode());
            }
        }
        for (String barcodeCandidate : barcodeCandidates) {
            if (productRepository.existsByBarcode(barcodeCandidate)) {
                throw new DuplicateBarcodeException(barcodeCandidate);
            }
        }
        Category resolvedCategory = resolveCategory(categoryId, category);
        Brand resolvedBrand = resolveBrand(brandId, brand);
        Product product = Product.create(sku, name,
                resolvedCategory == null ? category : resolvedCategory.name(), safeVariants,
                pricing == null ? Pricing.empty() : pricing, resolvedType, false,
                resolvedBrand == null ? brand : resolvedBrand.name(), imageUrl, onSale,
                superPromo, description, videoUrl, images, attributes,
                resolvedCategory == null ? null : resolvedCategory.id(),
                resolvedBrand == null ? null : resolvedBrand.id())
                .withBarcode(barcode)
                .withUnit(unit)
                .withSampleProduct(sampleProduct)
                .withKitComponentEligible(kitComponentEligible)
                .withVisibleInPos(visibleInPos == null || visibleInPos)
                .withVisibleInMarketplace(visibleInMarketplace == null || visibleInMarketplace)
                // PDV-F010: mesma convenção de visibleInPos — omitido resolve para o default da
                // migration (sai na mesa, não é sessão), nunca para false por acidente.
                .withAvailableForTable(mesa.availableForTable() == null || mesa.availableForTable())
                .withSessionProduct(Boolean.TRUE.equals(mesa.sessionProduct()))
                .withSessionsPerUnit(mesa.sessionsPerUnit())
                .withOpenRoshPrice(mesa.openRoshPrice())
                .withStatus(resolvedStatus);
        Product saved = productRepository.save(product);
        // Mesma transação da criação — se a receita falhar validação em algum item, o rollback
        // desfaz o produto também, fechando a janela de "kit órfão sem receita" do fluxo antigo
        // (POST seguido de PUT .../kit em duas chamadas separadas).
        if (recipe != null) {
            kitComponentRepository.replaceRecipe(sku, recipe);
        }
        // Mesma transação da criação (self-invocation do bean, mesmo idioma de
        // explodeKitMovement): se o depósito não existir ou faltar dado de lote, o rollback
        // desfaz o produto também — não há mais o estado "criado, mas sem estoque" de duas
        // chamadas separadas.
        if (initialStock != null) {
            adjustStock(saved.sku(), initialStock.warehouseCode(), MovementType.ENTRADA, initialStock.quantity(),
                    "Estoque inicial no cadastro", actorUsername, initialStock.lotCode(), initialStock.expiryDate());
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> listProducts(int page, int size, ProductFilter filter,
            ProductSortField sortField, SortDirection direction) {
        return productRepository.findAll(page, size, filter, sortField, direction);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> listActivePricedProducts(int page, int size, Boolean onSale, Long categoryId,
            String search) {
        return productRepository.findAllActiveAndPriced(page, size, onSale, categoryId, search);
    }

    @Override
    @Transactional
    public Product updateProduct(String sku, String name, String category, Pricing pricing, String brand,
            String imageUrl, Boolean onSale, Boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes, Long categoryId, String barcode,
            MeasurementUnit unit, Boolean sampleProduct, Boolean kitComponentEligible, Boolean visibleInPos,
            Boolean visibleInMarketplace, ProductStatus status, Long brandId,
            TableSessionCommand tableSession) {
        TableSessionCommand mesa = TableSessionCommand.orEmpty(tableSession);
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        // EST-F023: só conta contra o teto quem está ENTRANDO em RASCUNHO agora — editar um
        // rascunho que já era rascunho não pode contar duas vezes contra o próprio teto.
        if (status == ProductStatus.RASCUNHO && current.status() != ProductStatus.RASCUNHO
                && productRepository.countByStatus(ProductStatus.RASCUNHO) >= 5) {
            throw new DraftLimitReachedException();
        }
        Product updated = current.withDetails(name, category, brand, imageUrl, description, videoUrl);
        if (status != null) {
            updated = updated.withStatus(status);
        }
        // Categoria muda em par (id + nome denormalizado) ou não muda — nulos nos dois campos
        // mantêm o que já estava, seguindo a semântica de PATCH do resto do método.
        Category resolvedCategory = resolveCategory(categoryId, category);
        if (resolvedCategory != null) {
            updated = updated.withCategory(resolvedCategory.id(), resolvedCategory.name());
        }
        // Marca muda em par (id + nome denormalizado) ou não muda — mesmo raciocínio de categoria.
        Brand resolvedBrand = resolveBrand(brandId, brand);
        if (resolvedBrand != null) {
            updated = updated.withBrand(resolvedBrand.id(), resolvedBrand.name());
        }
        if (onSale != null) {
            updated = updated.withOnSale(onSale);
        }
        if (superPromo != null) {
            updated = updated.withSuperPromo(superPromo);
        }
        if (images != null) {
            updated = updated.withImages(images);
        }
        // Mesma semântica de images: nulo mantém, lista (inclusive vazia) substitui o conjunto.
        if (attributes != null) {
            updated = updated.withAttributes(attributes);
        }
        if (pricing != null) {
            // Custo de kit é sempre derivado da soma dos componentes (EST-F015) — um costPrice
            // digitado aqui viraria dado morto, sobrescrito na próxima leitura de
            // findPricingBySku. Rejeitado, não aceito e ignorado.
            if (current.isKit() && pricing.costPrice() != null) {
                throw new KitCostNotEditableException(sku);
            }
            // withPatch e não substituição: um PATCH que manda só o custo não pode apagar o
            // markup e o preço já cadastrados. Cada campo de Pricing carrega a mesma semântica
            // de "nulo mantém" que name e category têm em withDetails.
            updated = updated.withPricing(current.pricing().withPatch(pricing.costPrice(), pricing.markupPercent(),
                    pricing.salePrice(), pricing.originalPrice(), pricing.causeAmount()));
        }
        if (barcode != null && !barcode.equals(current.barcode()) && productRepository.existsByBarcode(barcode)) {
            throw new DuplicateBarcodeException(barcode);
        }
        if (barcode != null) {
            updated = updated.withBarcode(barcode);
        }
        if (unit != null) {
            updated = updated.withUnit(unit);
        }
        if (sampleProduct != null) {
            updated = updated.withSampleProduct(sampleProduct);
        }
        if (kitComponentEligible != null) {
            updated = updated.withKitComponentEligible(kitComponentEligible);
        }
        if (visibleInPos != null) {
            updated = updated.withVisibleInPos(visibleInPos);
        }
        if (visibleInMarketplace != null) {
            updated = updated.withVisibleInMarketplace(visibleInMarketplace);
        }
        // PDV-F010, mesma semântica de "nulo mantém" do resto deste método.
        if (mesa.availableForTable() != null) {
            updated = updated.withAvailableForTable(mesa.availableForTable());
        }
        if (mesa.sessionProduct() != null) {
            updated = updated.withSessionProduct(mesa.sessionProduct());
        }
        if (mesa.sessionsPerUnit() != null) {
            updated = updated.withSessionsPerUnit(mesa.sessionsPerUnit());
        }
        if (mesa.openRoshPrice() != null) {
            updated = updated.withOpenRoshPrice(mesa.openRoshPrice());
        }
        return productRepository.save(updated);
    }

    @Override
    @Transactional
    public Product addVariants(String sku, List<ProductVariant> newVariants) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        // Kit não tem grade (EST-F015) — mesma invariante de createProduct, aqui contra a porta
        // dos fundos de acrescentar variação a um kit já existente.
        if (current.isKit()) {
            throw new KitHasVariantsException(sku);
        }
        List<ProductVariant> safeNewVariants = newVariants == null ? List.of() : newVariants;
        // Mesma checagem de duplicidade de createProduct — as novas variações entram no mesmo
        // espaço de nomes compartilhado por todo o catálogo (SKU e barcode).
        Set<String> skuCandidates = new LinkedHashSet<>();
        for (ProductVariant variant : safeNewVariants) {
            if (!skuCandidates.add(variant.sku())) {
                throw new DuplicateSkuException(variant.sku());
            }
        }
        for (String candidate : skuCandidates) {
            if (productRepository.existsBySku(candidate)) {
                throw new DuplicateSkuException(candidate);
            }
        }
        Set<String> barcodeCandidates = new LinkedHashSet<>();
        for (ProductVariant variant : safeNewVariants) {
            if (variant.barcode() != null && !barcodeCandidates.add(variant.barcode())) {
                throw new DuplicateBarcodeException(variant.barcode());
            }
        }
        for (String barcodeCandidate : barcodeCandidates) {
            if (productRepository.existsByBarcode(barcodeCandidate)) {
                throw new DuplicateBarcodeException(barcodeCandidate);
            }
        }
        // Append, nunca substituição: toda variação já existente precisa continuar presente na
        // lista, ou o rebuild completo de ProductRepositoryImpl.save() a apagaria (EST-C011).
        List<ProductVariant> merged = new ArrayList<>(current.variants());
        merged.addAll(safeNewVariants);
        return productRepository.save(current.withVariants(merged));
    }

    @Override
    @Transactional
    public Product updateVariant(String productSku, String variantSku, Boolean active,
            List<ProductAttribute> attributes, Pricing pricing, String barcode) {
        Product current = productRepository.findBySku(productSku)
                .orElseThrow(() -> new ProductNotFoundException(productSku));
        ProductVariant target = current.variants().stream()
                .filter(v -> v.sku().equals(variantSku))
                .findFirst()
                .orElseThrow(() -> new ProductVariantNotFoundException(productSku, variantSku));
        if (barcode != null && !barcode.equals(target.barcode()) && productRepository.existsByBarcode(barcode)) {
            throw new DuplicateBarcodeException(barcode);
        }
        ProductVariant updated = target;
        if (active != null) {
            updated = updated.withActive(active);
        }
        if (attributes != null) {
            updated = updated.withAttributes(attributes);
        }
        if (barcode != null) {
            updated = updated.withBarcode(barcode);
        }
        if (pricing != null) {
            // withPatch e não substituição, mesma razão de updateProduct: um PATCH que manda só
            // o custo não pode apagar o preço de venda próprio já cadastrado na variação.
            Pricing basePricing = target.pricing() == null ? Pricing.empty() : target.pricing();
            updated = updated.withPricing(basePricing.withPatch(pricing.costPrice(), pricing.markupPercent(),
                    pricing.salePrice(), pricing.originalPrice(), pricing.causeAmount()));
        }
        ProductVariant finalUpdated = updated;
        List<ProductVariant> merged = current.variants().stream()
                .map(v -> v.sku().equals(variantSku) ? finalUpdated : v)
                .toList();
        return productRepository.save(current.withVariants(merged));
    }

    @Override
    @Transactional
    public Product deleteVariant(String productSku, String variantSku) {
        Product current = productRepository.findBySku(productSku)
                .orElseThrow(() -> new ProductNotFoundException(productSku));
        current.variants().stream()
                .filter(v -> v.sku().equals(variantSku))
                .findFirst()
                .orElseThrow(() -> new ProductVariantNotFoundException(productSku, variantSku));
        // stock_balance/stock_movement referenciam o SKU como texto livre, sem FK (EST-C011) —
        // apagar a variante deixaria esse histórico órfão. active:false preserva o histórico.
        if (stockBalanceRepository.existsBySku(variantSku) || stockMovementRepository.existsBySku(variantSku)) {
            throw new VariantHasStockHistoryException(productSku, variantSku);
        }
        List<ProductVariant> remaining = current.variants().stream()
                .filter(v -> !v.sku().equals(variantSku))
                .toList();
        return productRepository.save(current.withVariants(remaining));
    }

    @Override
    @Transactional
    public void deleteProduct(String sku) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        if (current.status() != ProductStatus.RASCUNHO) {
            throw new ProductNotDraftException(sku);
        }
        // A grade inteira precisa estar limpa, não só o SKU pai: criação atômica com estoque
        // inicial grava ENTRADA, e um rascunho que chegou a ser movimentado tem histórico que
        // ficaria órfão — stock_balance/stock_movement referenciam SKU como texto livre, sem FK
        // (EST-C011). Mesma régua de deleteVariant, aplicada ao agregado.
        if (hasStockHistory(sku)) {
            throw new ProductHasStockHistoryException(sku);
        }
        for (ProductVariant variant : current.variants()) {
            if (hasStockHistory(variant.sku())) {
                throw new ProductHasStockHistoryException(variant.sku());
            }
        }
        productRepository.deleteBySku(sku);
    }

    private boolean hasStockHistory(String sku) {
        return stockBalanceRepository.existsBySku(sku) || stockMovementRepository.existsBySku(sku);
    }

    // ── Categorias do catálogo ───────────────────────────────────────────────

    @Override
    @Transactional
    public Category createCategory(String name, boolean featured, int displayOrder) {
        categoryRepository.findByName(name).ifPresent(existing -> {
            throw new DuplicateCategoryNameException(name);
        });
        return categoryRepository.save(Category.create(name, featured, displayOrder));
    }

    @Override
    @Transactional
    public Category updateCategory(Long id, String name, Boolean featured, Integer displayOrder) {
        Category current = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        if (name != null) {
            // O nome é único; deixar passar um rename que colide daria erro de constraint no
            // flush, longe daqui e sem o errorCode que o contrato promete.
            categoryRepository.findByName(name)
                    .filter(other -> !other.id().equals(id))
                    .ifPresent(other -> {
                        throw new DuplicateCategoryNameException(name);
                    });
        }
        Category updated = categoryRepository.save(current.withDetails(name, featured, displayOrder));
        if (name != null && !name.equals(current.name())) {
            // Propaga para a coluna denormalizada dos produtos vinculados. Sem isso a vitrine
            // passaria a exibir o nome antigo e a ordenar pelo novo.
            productRepository.renameCategory(id, updated.name());
        }
        return updated;
    }

    @Override
    @Transactional
    public Category setCategoryActive(Long id, boolean active) {
        Category current = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        // Deliberadamente NÃO mexe nos produtos: categoria é organização de vitrine, não permissão
        // de venda. Desativá-la a tira das listas públicas; os produtos seguem à venda.
        return categoryRepository.save(current.withActive(active));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Category> listCategories(int page, int size) {
        return categoryRepository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> listActiveCategories() {
        return categoryRepository.findActiveOrdered();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countProductsByCategoryIds(List<Long> categoryIds) {
        return productRepository.countProductsByCategoryIds(categoryIds);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new CategoryHasProductsException(id, productCount);
        }
        categoryRepository.deleteById(id);
    }

    /**
     * Resolve o par (id, nome) de categoria de um produto, aceitando os <b>dois</b> caminhos.
     *
     * <p>É a peça que mantém o cadastro atual do admin funcionando sem nenhuma mudança: ele
     * continua mandando {@code category} como texto livre, e aqui esse texto vira — ou reencontra
     * — uma categoria de verdade. Quem já souber o id manda {@code categoryId} e o nome sai
     * resolvido a partir dele.</p>
     *
     * <p>Texto desconhecido <b>cria</b> a categoria, em vez de ser recusado. Recusar quebraria o
     * fluxo "Nova categoria..." do formulário e transformaria uma feature aditiva em mudança de
     * contrato; e o efeito colateral de um erro de digitação criar uma categoria a mais é
     * exatamente o que já acontecia quando categoria era texto puro — só que agora é visível e
     * editável na tela de categorias.</p>
     *
     * @return {@code null} quando não há categoria nenhuma a resolver (produto sem categoria
     *         segue sendo estado válido).
     */
    private Category resolveCategory(Long categoryId, String categoryName) {
        if (categoryId != null) {
            return categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        }
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        return categoryRepository.findByName(categoryName)
                .orElseGet(() -> categoryRepository.save(Category.create(categoryName)));
    }

    // ── Marcas do catálogo ────────────────────────────────────────────────────

    @Override
    @Transactional
    public Brand createBrand(String name) {
        brandRepository.findByName(name).ifPresent(existing -> {
            throw new DuplicateBrandNameException(name);
        });
        return brandRepository.save(Brand.create(name));
    }

    @Override
    @Transactional
    public Brand updateBrand(Long id, String name) {
        Brand current = brandRepository.findById(id)
                .orElseThrow(() -> new BrandNotFoundException(id));
        if (name != null) {
            brandRepository.findByName(name)
                    .filter(other -> !other.id().equals(id))
                    .ifPresent(other -> {
                        throw new DuplicateBrandNameException(name);
                    });
        }
        Brand updated = brandRepository.save(current.withDetails(name));
        if (name != null && !name.equals(current.name())) {
            productRepository.renameBrand(id, updated.name());
        }
        return updated;
    }

    @Override
    @Transactional
    public Brand setBrandActive(Long id, boolean active) {
        Brand current = brandRepository.findById(id)
                .orElseThrow(() -> new BrandNotFoundException(id));
        return brandRepository.save(current.withActive(active));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Brand> listBrands(String search, int page, int size) {
        return search == null || search.isBlank()
                ? brandRepository.findAll(page, size)
                : brandRepository.findByNameContaining(search, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> listActiveBrands() {
        return brandRepository.findActiveOrdered();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countProductsByBrandIds(List<Long> brandIds) {
        return productRepository.countProductsByBrandIds(brandIds);
    }

    @Override
    @Transactional
    public void deleteBrand(Long id) {
        brandRepository.findById(id).orElseThrow(() -> new BrandNotFoundException(id));
        long productCount = productRepository.countByBrandId(id);
        if (productCount > 0) {
            throw new BrandHasProductsException(id, productCount);
        }
        brandRepository.deleteById(id);
    }

    /**
     * Resolve o par (id, nome) de marca de um produto — mesmo raciocínio de
     * {@link #resolveCategory}, com a mesma leniência de criar a marca quando o texto não bate com
     * nenhuma existente.
     *
     * @return {@code null} quando não há marca nenhuma a resolver (produto sem marca segue sendo
     *         estado válido).
     */
    private Brand resolveBrand(Long brandId, String brandName) {
        if (brandId != null) {
            return brandRepository.findById(brandId)
                    .orElseThrow(() -> new BrandNotFoundException(brandId));
        }
        if (brandName == null || brandName.isBlank()) {
            return null;
        }
        return brandRepository.findByName(brandName)
                .orElseGet(() -> brandRepository.save(Brand.create(brandName)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> averageMarginPercentByCategoryIds(List<Long> categoryIds) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Long id : categoryIds) {
            result.put(id, averageMarginPercent(productRepository.findAllByCategoryId(id)));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> averageMarginPercentByBrandIds(List<Long> brandIds) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Long id : brandIds) {
            result.put(id, averageMarginPercent(productRepository.findAllByBrandId(id)));
        }
        return result;
    }

    /**
     * Média simples de {@code Pricing#marginPercent()} entre os produtos informados, ignorando os
     * que não têm margem calculável (sem custo, sem preço efetivo, ou preço efetivo zero — mesmas
     * condições de {@code marginPercent()}). Mesma fórmula do relatório de margem do financeiro
     * ({@code OrderReportRepositoryImpl.summarizeMargin}), aqui promediada por produto do catálogo
     * em vez de agregada sobre histórico de vendas.
     *
     * @return {@code null} se nenhum produto contribui com margem calculável.
     */
    private BigDecimal averageMarginPercent(List<Product> products) {
        List<BigDecimal> margins = products.stream()
                .map(p -> p.pricing().marginPercent())
                .filter(Objects::nonNull)
                .toList();
        if (margins.isEmpty()) {
            return null;
        }
        BigDecimal sum = margins.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(margins.size()), 2, RoundingMode.HALF_UP);
    }

    // ── Vocabulário de atributos ─────────────────────────────────────────────

    @Override
    @Transactional
    public AttributeType createAttributeType(String name) {
        attributeTypeRepository.findByName(name).ifPresent(existing -> {
            throw new DuplicateAttributeTypeNameException(name);
        });
        return attributeTypeRepository.save(AttributeType.create(name));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttributeType> listAttributeTypes() {
        return attributeTypeRepository.findAllOrderByName();
    }

    // ── Lista de Reposição ────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReplenishmentListItem upsertReplenishmentItem(String sku, String warehouseCode, BigDecimal quantity,
            String note, String actorUsername) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        Product product = productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        BigDecimal currentStock = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .map(StockBalance::quantity)
                .orElse(BigDecimal.ZERO);
        BigDecimal minStock = reorderPointRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .map(ReorderPoint::minQuantity)
                .orElse(null);
        BigDecimal suggestedQuantity = minStock == null ? null
                : minStock.subtract(currentStock).max(BigDecimal.ZERO);
        // Custo efetivo do SKU específico (EST-F020: variação pode ter custo próprio), não sempre
        // o do pai — mesma resolução que o PDV e a vitrine já usam.
        BigDecimal unitCost = product.effectivePricingFor(sku).costPrice();
        // Última ENTRADA deste SKU/depósito — mesma consulta usada pelo histórico de compras
        // (item 2), reaproveitada para não duplicar a lógica de "última compra".
        PageResult<StockMovement> lastPurchase = stockMovementRepository
                .findEntradasBySkuAndWarehouseId(sku, warehouse.id(), 0, 1);
        StockMovement previous = lastPurchase.content().isEmpty() ? null : lastPurchase.content().get(0);

        ReplenishmentListItem item = ReplenishmentListItem.create(sku, warehouse.id(), product.name(),
                product.category(), product.brand(), product.unit(), currentStock, minStock, suggestedQuantity,
                quantity, unitCost,
                previous == null ? null : previous.quantity(),
                previous == null ? null : previous.unitCost(),
                previous == null ? null : previous.createdAt(),
                note, actorUsername);
        return replenishmentListRepository.save(item);
    }

    @Override
    @Transactional
    public ReplenishmentListItem updateReplenishmentItem(String sku, String warehouseCode, BigDecimal quantity,
            String note) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        ReplenishmentListItem current = replenishmentListRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseThrow(() -> new ReplenishmentItemNotFoundException(sku, warehouseCode));
        return replenishmentListRepository.save(current.withQuantityAndNote(quantity, note));
    }

    @Override
    @Transactional
    public void deleteReplenishmentItem(String sku, String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        replenishmentListRepository.deleteBySkuAndWarehouseId(sku, warehouse.id());
    }

    @Override
    @Transactional
    public void clearReplenishmentList(String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        replenishmentListRepository.deleteByWarehouseId(warehouse.id());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReplenishmentListItem> listReplenishmentItems(String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        return replenishmentListRepository.findByWarehouseId(warehouse.id());
    }

    @Override
    @Transactional(readOnly = true)
    public Pricing findPricingBySku(String sku) {
        // findByAnySku e não findBySku: o SKU lido no balcão pode ser o do pai ou o de uma
        // variação, e o chamador não precisa saber qual — a resolução acontece aqui.
        Product product = productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        // Kit nunca tem variação (KitHasVariantsException), então a precedência não se aplica.
        return product.isKit() ? derivedKitPricing(product) : product.effectivePricingFor(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogSaleInfo resolveSaleInfo(String sku) {
        // Mesma resolução de findPricingBySku, numa consulta só — nome não precisa de derivação
        // (mora sempre no pai, kit ou não), só a precificação precisa.
        Product product = productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        // EST-F036 — a base de um produto com variações não se vende (PDV, mesa, checkout): todos os
        // caminhos de venda resolvem o item por aqui, e a recusa sai antes de qualquer gravação.
        if (!product.isSellable(sku)) {
            throw new ParentNotSellableException(sku);
        }
        Pricing pricing = product.isKit() ? derivedKitPricing(product) : product.effectivePricingFor(sku);
        // availableForTable/sessionProduct/openRoshPrice vêm do PAI, mesmo quando o SKU pedido é o
        // de uma variação: disponibilidade na mesa e preço de open rosh não têm versão por sabor.
        // PDV-F042 — o SKU pedido é a BASE de um produto com variações: o estoque real está nos
        // sabores/cores, e quem precisa saber disso (a essência da sessão) recusa a base.
        boolean parentWithVariants = product.sku().equals(sku) && !product.variants().isEmpty();
        return new CatalogSaleInfo(product.name(), pricing, product.availableForTable(),
                product.sessionProduct(), product.openRoshPrice(), product.sessionsPerUnit(),
                product.isKit(), parentWithVariants);
    }



    /**
     * Custo do kit é a soma de {@code costPrice * quantity} dos componentes — nunca digitado
     * (EST-F015, §2.10). O {@code salePrice} continua sendo o do kit; {@code markupPercent} é
     * sempre nulo no derivado, porque não é input de ninguém para um kit.
     *
     * <p>Um componente sem custo próprio torna o custo do kit inteiro {@code null} — seguindo a
     * convenção já estabelecida em {@link Pricing} de que ausência é "desconhecido", nunca zero.
     * Isso propaga honestamente: {@code marginPercent()}/{@code marginAmount()}/
     * {@code isBelowCost()} do kit também viram {@code null}, sem precisar de matemática nova.</p>
     */
    private Pricing derivedKitPricing(Product kit) {
        List<KitComponent> recipe = kitComponentRepository.findByKitSku(kit.sku());
        BigDecimal totalCost = BigDecimal.ZERO;
        for (KitComponent component : recipe) {
            // Componente é garantidamente SIMPLES (validado em defineKitRecipe) — lê o Pricing
            // próprio dele direto, sem reentrar em findPricingBySku.
            Product componentProduct = productRepository.findByAnySku(component.componentSku())
                    .orElseThrow(() -> new ProductNotFoundException(component.componentSku()));
            // Pela mesma precedência de findPricingBySku: se o componente é uma variação com
            // custo próprio, é esse custo que entra na soma do kit — não o do pai dela.
            BigDecimal componentCost =
                    componentProduct.effectivePricingFor(component.componentSku()).costPrice();
            if (componentCost == null) {
                totalCost = null;
                break;
            }
            totalCost = totalCost.add(componentCost.multiply(component.quantity()));
        }
        return Pricing.of(totalCost, null, kit.pricing().salePrice());
    }

    @Override
    @Transactional
    public Product changeSku(String currentSku, String newSku) {
        String target = newSku == null ? null : newSku.trim();
        if (target == null || target.isEmpty()) {
            throw new IllegalArgumentException("newSku é obrigatório");
        }
        if (!productRepository.existsBySku(currentSku)) {
            throw new ProductNotFoundException(currentSku);
        }
        if (!target.equals(currentSku)) {
            // Pai e variações dividem o espaço de nomes (uk_product_sku e uk_product_variant_sku),
            // e existsBySku olha os dois — mesma checagem do createProduct.
            if (productRepository.existsBySku(target)) {
                throw new DuplicateSkuException(target);
            }
            productRepository.renameSku(currentSku, target);
        }
        return productRepository.findByAnySku(target)
                .orElseThrow(() -> new ProductNotFoundException(target));
    }

    @Override
    @Transactional(readOnly = true)
    public Product findProductBySku(String sku) {
        // Mesma razão de findPricingBySku: a variação não tem categoria própria, herda a do pai.
        return productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
    }

    @Override
    @Transactional(readOnly = true)
    public EstoqueSummary getSummary() {
        ReorderAlertCounts alerts = reorderPointRepository.countAlerts();
        return new EstoqueSummary(
                productRepository.countProducts(),
                productRepository.countVariants(),
                stockBalanceRepository.sumInventoryValueAtCost(),
                alerts.criticos(),
                alerts.atencao(),
                productRepository.findCategoryWithMostProducts().orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public Product findProductByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new BarcodeNotFoundException(barcode));
    }

    @Override
    @Transactional
    public Product setProductActive(String sku, boolean active) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        return productRepository.save(current.withActive(active));
    }

    @Override
    @Transactional
    public Product setParentSellable(String sku, boolean parentSellable) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        return productRepository.save(current.withParentSellable(parentSellable));
    }

    @Override
    @Transactional
    public Product setProductLotTracked(String sku, boolean lotTracked) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        // Kit x lote-rastreado é mutuamente exclusivo — o compact constructor de Product já barra
        // isso, então não há checagem duplicada aqui.
        return productRepository.save(current.withLotTracked(lotTracked));
    }

    @Override
    @Transactional
    public Warehouse updateWarehouse(String code, String name, WarehouseType type) {
        Warehouse current = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException(code));
        return warehouseRepository.save(current.withDetails(name, type));
    }

    @Override
    @Transactional
    public Warehouse setWarehouseActive(String code, boolean active) {
        Warehouse current = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException(code));
        return warehouseRepository.save(current.withActive(active));
    }

    @Override
    @Transactional
    public Warehouse createWarehouse(String code, String name, WarehouseType type) {
        warehouseRepository.findByCode(code).ifPresent(w -> {
            throw new DuplicateWarehouseCodeException(code);
        });
        return warehouseRepository.save(Warehouse.create(code, name, type));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Warehouse> listWarehouses(int page, int size) {
        return warehouseRepository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public StockBalance getStockBalance(String sku, String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        Optional<Product> product = productRepository.findByAnySku(sku);
        if (product.isPresent() && product.get().isKit()) {
            return derivedKitBalance(product.get(), warehouse.id());
        }
        return stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockBalance> listStockBalances(String warehouseCode, int page, int size) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        return stockBalanceRepository.findByWarehouseId(warehouse.id(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLot> listStockLots(String sku, String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        return stockLotRepository.findBySkuAndWarehouseId(sku, warehouse.id());
    }

    /**
     * {@code min(floor(saldo_componente_disponível / quantidade_na_receita))} sobre os
     * componentes (EST-F015, §2.10). Usa o <b>disponível</b>, não o físico bruto — consistente
     * com a regra já estabelecida em EST-F021 de que toda saída nova valida contra o disponível:
     * um componente reservado para outro pedido não está de fato livre para montar este kit
     * agora. Recipe vazia (produto ainda não teve receita definida) devolve zero.
     */
    private StockBalance derivedKitBalance(Product kit, Long warehouseId) {
        return StockBalance.derived(kit.sku(), warehouseId, computeKitAvailability(kit, warehouseId).buildableQuantity());
    }

    /**
     * Mesmo cálculo de {@link #derivedKitBalance}, com o detalhamento por componente exposto —
     * base de {@code GET /estoque/kits/availability} (Bloco 1.1) e da detecção de transição
     * "kit ficou bloqueado" (Bloco 1.2). Extraído para não duplicar a regra em dois lugares.
     */
    private KitAvailability computeKitAvailability(Product kit, Long warehouseId) {
        List<KitComponent> recipe = kitComponentRepository.findByKitSku(kit.sku());
        if (recipe.isEmpty()) {
            return new KitAvailability(kit.sku(), kit.name(), BigDecimal.ZERO, true, List.of());
        }
        List<KitComponentAvailability> components = new ArrayList<>();
        BigDecimal minKits = null;
        for (KitComponent component : recipe) {
            StockBalance componentBalance = stockBalanceRepository
                    .findBySkuAndWarehouseId(component.componentSku(), warehouseId)
                    .orElseGet(() -> StockBalance.zero(component.componentSku(), warehouseId));
            BigDecimal possibleKits = componentBalance.availableQuantity()
                    .divide(component.quantity(), 0, RoundingMode.FLOOR);
            components.add(new KitComponentAvailability(component.componentSku(), component.quantity(),
                    componentBalance.availableQuantity(), possibleKits));
            if (minKits == null || possibleKits.compareTo(minKits) < 0) {
                minKits = possibleKits;
            }
        }
        return new KitAvailability(kit.sku(), kit.name(), minKits, minKits.signum() == 0, components);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<KitAvailability> getKitAvailability(String warehouseCode, Boolean blocked, int page, int size) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        List<KitAvailability> all = productRepository.findAllByType(ProductType.KIT).stream()
                .map(kit -> computeKitAvailability(kit, warehouse.id()))
                .toList();
        List<KitAvailability> filtered = blocked == null ? all
                : all.stream().filter(k -> k.blocked() == blocked).toList();
        int total = filtered.size();
        int totalPages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return new PageResult<>(filtered.subList(from, to), page, size, total, totalPages);
    }

    @Override
    @Transactional
    public StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username) {
        return adjustStock(sku, warehouseCode, type, quantity, reason, username, null, null);
    }

    /**
     * EST-F025 — conversão entre SKUs. Ver o javadoc de {@link EstoqueUseCase#convertStock}.
     *
     * <p><b>A SAIDA vem primeiro de propósito.</b> É o lado que pode faltar saldo; falhar antes de
     * criar a entrada mantém a regra de "valida tudo antes de escrever" que {@code registerSale} e
     * {@code ComandaService.addItem} já seguem, e deixa o erro apontando para o problema real (não há
     * lata) em vez de para um estado meio aplicado.</p>
     *
     * <p>As duas chamadas a {@link #adjustStock} são <b>autoinvocação do próprio bean</b>: não passam
     * pelo proxy Spring e por isso correm na MESMA transação aberta aqui, que é exatamente a
     * atomicidade que esta operação existe para dar. Mesmo idioma, e mesma razão, de
     * {@link #explodeKitMovement} — parece descuido e não é.</p>
     *
     * <p>Nada mais precisa ser validado aqui: existência de SKU e depósito, {@code @Version}, alerta
     * de reposição, explosão de kit e FEFO já moram dentro de {@code adjustStock}, e cada lado da
     * conversão passa por todos eles.</p>
     */
    /**
     * EST-F011 — curva ABC. O service faz o mínimo: resolve o depósito, pede as linhas agregadas e
     * entrega a {@link AbcAnalysis}. A classificação é aritmética pura de domínio, e mantê-la fora
     * daqui é o que a torna testável sem banco — mesma divisão de {@code DiscountProration}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AbcAnalysis.AbcEntry> findAbcAnalysis(String warehouseCode, Instant from, Instant to) {
        Long warehouseId = warehouseCode == null ? null : requireWarehouse(warehouseCode).id();
        return AbcAnalysis.classify(stockMovementRepository.findConsumptionByPeriod(warehouseId, from, to));
    }

    @Override
    @Transactional
    public StockConversionResult convertStock(String fromSku, String toSku, BigDecimal fromQuantity,
            BigDecimal toQuantity, String warehouseCode, String reason, String username) {
        if (fromSku != null && fromSku.equals(toSku)) {
            throw new SameSkuConversionException(fromSku);
        }
        StockBalance from = adjustStock(fromSku, warehouseCode, MovementType.SAIDA, fromQuantity,
                "Conversão para " + toSku + " — " + reason, username);
        StockBalance to = adjustStock(toSku, warehouseCode, MovementType.ENTRADA, toQuantity,
                "Conversão de " + fromSku + " — " + reason, username);
        return new StockConversionResult(from, to);
    }

    // ── Lata aberta (EST-F027) ───────────────────────────────────────────────
    //
    // A conversão acima (EST-F025) e a lata resolvem o mesmo problema físico por caminhos
    // diferentes, e a fronteira precisa ficar escrita para o operador não terminar com duas
    // verdades sobre a mesma lata: `POST /estoque/conversions` continua sendo a ferramenta
    // GENÉRICA de reembalagem entre dois SKUs distintos (comprei em fardo, vendo em unidade), e a
    // lata é o caminho da ESSÊNCIA, onde origem e sessão são o MESMO SKU — o produto de sessão,
    // com openRoshPrice e sessionsPerUnit próprios. Essência não passa mais pela conversão.

    @Override
    @Transactional
    public OpenPackage consumeSession(String sku, String warehouseCode, BigDecimal quantity, String username) {
        Product product = productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        if (!product.sessionProduct() || product.sessionsPerUnit() == null || product.sessionsPerUnit() <= 0) {
            throw new NotAPackagedSessionProductException(sku);
        }
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        int sessions = sessionsFor(quantity);

        OpenPackage current = openPackageRepository.findOpenForUpdate(sku, warehouse.id()).orElse(null);
        // A lata esgotada continua aberta até a sessão SEGUINTE — é ela que o atendente está
        // usando até o fim, e é o que permite a tela mostrar "5 de 5". Quem a fecha é este ponto.
        if (current != null && current.isExhausted()) {
            openPackageRepository.save(current.closed(OpenPackageCloseReason.EXHAUSTED, Instant.now()));
            current = null;
        }
        if (current == null) {
            current = openPackage(sku, warehouse, product.sessionsPerUnit(), username);
        }
        return openPackageRepository.save(current.withUses(sessions));
    }

    @Override
    @Transactional
    public void releaseSession(String sku, String warehouseCode, BigDecimal quantity) {
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        openPackageRepository.findOpenForUpdate(sku, warehouse.id())
                .ifPresent(open -> openPackageRepository.save(open.withoutUses(sessionsFor(quantity))));
    }

    @Override
    @Transactional
    public OpenPackage replaceOpenPackage(String sku, String warehouseCode, String username) {
        Product product = productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        if (!product.sessionProduct() || product.sessionsPerUnit() == null || product.sessionsPerUnit() <= 0) {
            throw new NotAPackagedSessionProductException(sku);
        }
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));

        Optional<OpenPackage> current = openPackageRepository.findOpenForUpdate(sku, warehouse.id());
        // A nova é aberta ANTES de fechar a velha, e a ordem importa: abrir baixa estoque e pode
        // faltar saldo. Falhar depois de fechar deixaria o atendente sem lata nenhuma no sistema,
        // com uma na mão. Mesma razão pela qual convertStock faz a SAIDA primeiro.
        OpenPackage replacement = openPackage(sku, warehouse, product.sessionsPerUnit(), username);
        current.ifPresent(open ->
                openPackageRepository.save(open.closed(OpenPackageCloseReason.REPLACED, Instant.now())));
        return openPackageRepository.save(replacement);
    }

    @Override
    @Transactional
    public OpenPackage registerOpenPackage(String sku, String warehouseCode, int usesRemaining, String username) {
        Product product = productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        if (!product.sessionProduct() || product.sessionsPerUnit() == null || product.sessionsPerUnit() <= 0) {
            throw new NotAPackagedSessionProductException(sku);
        }
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        // A checagem dá a mensagem certa no caso comum; a corrida entre dois cadastros simultâneos
        // é barrada pelo índice parcial da V124 e traduzida no GlobalExceptionHandler.
        if (openPackageRepository.findOpenForUpdate(sku, warehouse.id()).isPresent()) {
            throw new OpenPackageAlreadyOpenException(sku, warehouseCode);
        }
        // Sem adjustStock, de propósito: ver o javadoc do port.
        return openPackageRepository.save(OpenPackage.registered(sku, warehouse.id(), product.sessionsPerUnit(),
                usesRemaining, username, Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenPackage> listOpenPackages(String warehouseCode) {
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        return openPackageRepository.findAllOpen(warehouse.id());
    }

    @Override
    @Transactional(readOnly = true)
    public OpenPackage findOpenPackage(String sku, String warehouseCode) {
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        return openPackageRepository.findOpen(sku, warehouse.id())
                .orElseThrow(() -> new OpenPackageNotFoundException(sku, warehouseCode));
    }

    /**
     * Tira uma unidade da prateleira e devolve a lata nova, ainda zerada. A {@code SAIDA} é
     * autoinvocação do próprio bean — mesma transação, mesmo idioma de {@code convertStock} e
     * {@code explodeKitMovement} —, então validação de SKU, {@code @Version}, alerta de reposição e
     * FEFO acontecem normalmente.
     */
    private OpenPackage openPackage(String sku, Warehouse warehouse, int sessionsPerUnit, String username) {
        adjustStock(sku, warehouse.code(), MovementType.SAIDA, BigDecimal.ONE,
                "Abertura de lata (" + sessionsPerUnit + " sessões)", username);
        return OpenPackage.open(sku, warehouse.id(), sessionsPerUnit, username, Instant.now());
    }

    /**
     * Quantidade de sessões que uma linha consome. Arredonda para <b>cima</b>: não existe meia lata
     * aberta, e meia sessão gasta um uso inteiro. Piso em 1 para quantidade positiva menor que um.
     */
    private static int sessionsFor(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantidade de sessões deve ser maior que zero");
        }
        return Math.max(1, quantity.setScale(0, RoundingMode.CEILING).intValueExact());
    }

    @Override
    @Transactional
    public StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode, LocalDate expiryDate) {
        return adjustStock(sku, warehouseCode, type, quantity, reason, username, lotCode, expiryDate, null);
    }

    @Override
    @Transactional
    public StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode, LocalDate expiryDate, BigDecimal unitCost,
            Long goodsReceiptId) {
        requireKnownSku(sku);
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        Optional<Product> product = productRepository.findByAnySku(sku);
        if (product.isPresent() && product.get().isKit()) {
            // Kit não tem lote próprio (EST-F015: sem saldo físico próprio, e Product já barra
            // lotTracked num kit) — recusar explicitamente em vez de explodeKitMovement descartar
            // lotCode/expiryDate em silêncio na recursão para os componentes.
            if ((lotCode != null && !lotCode.isBlank()) || expiryDate != null) {
                throw new UnexpectedLotInfoException(sku, "kit não tem lote próprio");
            }
            if (unitCost != null) {
                throw new UnexpectedUnitCostException(sku, "kit não tem saldo próprio, não acumula custo médio");
            }
            return explodeKitMovement(product.get(), warehouse, type, quantity, reason, username);
        }
        requireActiveForInbound(sku, warehouse, type);
        // EST-F036 — entrada na base de um produto com variações também é recusada: a compra entra
        // nas variações. SAIDA e AJUSTE passam, para escoar e corrigir no balanço o que ficou na base.
        if (type == MovementType.ENTRADA && product.isPresent() && !product.get().isSellable(sku)) {
            throw new ParentNotSellableException(sku);
        }
        boolean lotTracked = product.map(Product::lotTracked).orElse(false);
        validateLotInfo(sku, type, lotTracked, lotCode, expiryDate);
        validateUnitCost(sku, type, unitCost);

        // Fotografado ANTES da mutação — é o que permite detectar depois a transição "kit ficou
        // sem estoque de componente" (Bloco 1.2), comparando contra o buildable recalculado após
        // salvar. Kit vendido via explodeKitMovement recursa por componente até este mesmo método,
        // então a checagem cobre a cascata sem precisar de tratamento especial.
        Map<String, BigDecimal> kitBuildableBefore = kitBuildableSnapshot(sku, warehouse.id());

        StockBalance current = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
        // EST-F032 — se o filho não cobre a saída, abre embalagens pai até cobrir, e a saída segue
        // sobre o saldo já reposto. Só relê quando houve quebra: o caminho comum não paga nada a mais.
        if (type == MovementType.SAIDA && breakPackagingIfShort(sku, warehouse, current, quantity, username)) {
            current = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                    .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
        }
        StockBalance updated = current.apply(type, quantity, unitCost);

        String movementLotCode = null;
        if (lotTracked && type == MovementType.ENTRADA) {
            receiveIntoLot(sku, warehouse.id(), lotCode, expiryDate, quantity);
            movementLotCode = lotCode;
        }

        stockMovementRepository.save(StockMovement.create(sku, warehouse.id(), type, quantity, reason, username,
                movementLotCode, unitCost, goodsReceiptId));
        StockBalance saved = stockBalanceRepository.save(updated);

        if (lotTracked && type == MovementType.SAIDA) {
            consumeLotsFefo(sku, warehouse.id(), quantity);
        }

        notifyIfBelowReorderPoint(saved);
        notifyIfKitsNewlyBlocked(kitBuildableBefore, warehouse.id());
        return saved;
    }

    // ── Embalagem (EST-F032) ────────────────────────────────────────────────────────────────

    /** Teto da cadeia: fardo → carteira → maço → unidade. */
    private static final int MAX_PACKAGING_LINKS = 3;

    /**
     * Quebra automática de embalagem (EST-F032): a saída de {@code quantity} não cabe no
     * <b>disponível</b> do SKU, e ele está dentro de uma embalagem — abre-se o necessário do pai,
     * para cima (não existe meio maço), e o filho recebe o conteúdo.
     *
     * <p>As duas pontas são autoinvocação de {@link #adjustStock}: mesma transação da saída que
     * disparou a quebra, e é isso que faz a cascata acontecer sozinha — o maço que não tem saldo
     * abre a carteira pelo mesmo caminho. Se nem a cadeia inteira cobre, a {@code SAIDA} do pai
     * lança {@code InsufficientStockException} e a venda reverte junto. O filho entra com o custo
     * médio do pai dividido pelo fator, para o custo médio dele não sair do ar.</p>
     *
     * <p>Olha o <b>disponível</b>, não o físico: unidade reservada para um pedido online não está à
     * venda, e a quebra cobre só o que falta além dela.</p>
     *
     * @return se abriu alguma embalagem — o chamador relê o saldo do filho só nesse caso
     */
    private boolean breakPackagingIfShort(String sku, Warehouse warehouse, StockBalance current, BigDecimal quantity,
            String username) {
        BigDecimal missing = quantity.subtract(current.availableQuantity());
        if (missing.signum() <= 0) {
            return false;
        }
        Optional<SkuPackaging> link = skuPackagingRepository.findByChild(sku);
        if (link.isEmpty()) {
            return false;
        }
        SkuPackaging packaging = link.get();
        BigDecimal parents = packaging.parentsToOpen(missing);
        StockBalance parent = adjustStock(packaging.parentSku(), warehouse.code(), MovementType.SAIDA, parents,
                "Quebra automática de embalagem → " + sku, username);
        BigDecimal unitCost = parent.averageCost() == null ? null
                : parent.averageCost().divide(BigDecimal.valueOf(packaging.unitsPerParent()),
                        Money.INTERMEDIATE_SCALE, Money.ROUNDING);
        BigDecimal childUnits = packaging.childUnits(parents);
        adjustStock(sku, warehouse.code(), MovementType.ENTRADA, childUnits,
                "Quebra automática de embalagem ← " + packaging.parentSku(), username, null, null, unitCost, null);
        // A venda que disparou a quebra publica o que sempre publicou; este evento guarda a decisão
        // do SISTEMA de abrir a embalagem, que não tem autor humano.
        auditEvents.publish(AuditEvent.of(AuditEvent.EventType.STOCK_PACKAGE_BROKEN, username, Map.of(
                "parentSku", packaging.parentSku(),
                "childSku", sku,
                "warehouseCode", warehouse.code(),
                "parentsOpened", parents,
                "childUnits", childUnits)));
        return true;
    }

    @Override
    @Transactional
    public SkuPackaging definePackaging(String childSku, String parentSku, int unitsPerParent) {
        SkuPackaging packaging = new SkuPackaging(childSku, parentSku, unitsPerParent);
        requirePackageableSku(childSku);
        requirePackageableSku(parentSku);

        // Sobe a cadeia a partir do pai: chegar no filho é ciclo, e a quebra recursaria para sempre.
        int linksAbove = 0;
        String current = parentSku;
        Optional<SkuPackaging> up = skuPackagingRepository.findByChild(current);
        while (up.isPresent()) {
            if (up.get().parentSku().equals(childSku)) {
                throw new InvalidPackagingException("ligação criaria um ciclo: " + childSku
                        + " já está acima de " + parentSku);
            }
            linksAbove++;
            current = up.get().parentSku();
            up = skuPackagingRepository.findByChild(current);
        }
        if (linksAbove + 1 + linksBelow(childSku, 0) > MAX_PACKAGING_LINKS) {
            throw new InvalidPackagingException("a cadeia de embalagem passaria de " + (MAX_PACKAGING_LINKS + 1)
                    + " níveis (ex.: fardo → carteira → maço → unidade)");
        }
        return skuPackagingRepository.save(packaging);
    }

    @Override
    @Transactional
    public void removePackaging(String childSku) {
        if (skuPackagingRepository.findByChild(childSku).isEmpty()) {
            throw new PackagingNotFoundException(childSku);
        }
        skuPackagingRepository.deleteByChild(childSku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstoqueUseCase.PackagingLevel> getPackagingChain(String sku, String warehouseCode) {
        requireKnownSku(sku);
        Warehouse warehouse = warehouseCode == null ? null : requireWarehouse(warehouseCode);
        // De cima para baixo: sobe até a embalagem mais externa e desce pelo primeiro filho de cada
        // nível. Na prática a cadeia é uma linha (carteira → maço → unidade); um pai com dois
        // filhos aparece pelo de menor SKU.
        java.util.Deque<String> chain = new java.util.ArrayDeque<>();
        chain.add(sku);
        Optional<SkuPackaging> up = skuPackagingRepository.findByChild(sku);
        while (up.isPresent()) {
            chain.addFirst(up.get().parentSku());
            up = skuPackagingRepository.findByChild(up.get().parentSku());
        }
        String below = sku;
        Optional<SkuPackaging> down = firstChild(below);
        while (down.isPresent()) {
            chain.addLast(down.get().childSku());
            down = firstChild(down.get().childSku());
        }
        List<EstoqueUseCase.PackagingLevel> levels = new ArrayList<>();
        for (String level : chain) {
            Optional<SkuPackaging> contains = firstChild(level);
            BigDecimal available = warehouse == null ? null
                    : stockBalanceRepository.findBySkuAndWarehouseId(level, warehouse.id())
                            .map(StockBalance::availableQuantity).orElse(BigDecimal.ZERO);
            levels.add(new EstoqueUseCase.PackagingLevel(level, contains.map(SkuPackaging::childSku).orElse(null),
                    contains.map(SkuPackaging::unitsPerParent).orElse(null), available));
        }
        return levels;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstoqueUseCase.PackagedFamily> listPackagedFamilies(String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        List<SkuPackaging> links = skuPackagingRepository.findAll();
        Set<String> children = new LinkedHashSet<>();
        links.forEach(l -> children.add(l.childSku()));
        // Raiz = embalagem que não está dentro de nenhuma outra (a carteira, ou o fardo).
        List<String> roots = links.stream().map(SkuPackaging::parentSku)
                .filter(sku -> !children.contains(sku))
                .distinct().sorted().toList();

        // Uma consulta de produto e uma de saldo por nível — N+1 assumido, como o de toResponse das
        // latas: a central tem algumas dezenas de SKUs, não o catálogo inteiro.
        Map<String, Product> products = new LinkedHashMap<>();
        Map<String, List<EstoqueUseCase.PackagedLine>> lines = new LinkedHashMap<>();
        for (String root : roots) {
            Optional<Product> product = productRepository.findByAnySku(root);
            if (product.isEmpty() || !product.get().active()) {
                continue;
            }
            Product p = product.get();
            List<EstoqueUseCase.PackagedLevel> levels = new ArrayList<>();
            String level = root;
            while (level != null) {
                Optional<SkuPackaging> contains = firstChild(level);
                BigDecimal available = stockBalanceRepository.findBySkuAndWarehouseId(level, warehouse.id())
                        .map(StockBalance::availableQuantity).orElse(BigDecimal.ZERO);
                levels.add(new EstoqueUseCase.PackagedLevel(level, packagedLabel(p, level),
                        p.effectivePricingFor(level).effectivePrice(), available,
                        contains.map(SkuPackaging::childSku).orElse(null),
                        contains.map(SkuPackaging::unitsPerParent).orElse(null)));
                level = contains.map(SkuPackaging::childSku).orElse(null);
            }
            products.putIfAbsent(p.sku(), p);
            lines.computeIfAbsent(p.sku(), k -> new ArrayList<>()).add(new EstoqueUseCase.PackagedLine(levels));
        }
        return products.values().stream()
                .sorted(java.util.Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new EstoqueUseCase.PackagedFamily(p.sku(), p.name(), lines.get(p.sku())))
                .toList();
    }

    /** "azul · maço": os atributos da variação, na ordem cadastrada; o nome do produto sem variação. */
    private static String packagedLabel(Product product, String sku) {
        return product.variants().stream()
                .filter(v -> v.sku().equals(sku))
                .findFirst()
                .filter(v -> !v.attributes().isEmpty())
                .map(v -> v.attributes().stream().map(ProductAttribute::value)
                        .collect(java.util.stream.Collectors.joining(" · ")))
                .orElse(product.name());
    }

    private Optional<SkuPackaging> firstChild(String parentSku) {
        return skuPackagingRepository.findByParent(parentSku).stream()
                .min(java.util.Comparator.comparing(SkuPackaging::childSku));
    }

    /** Ligações abaixo de {@code sku} no ramo mais fundo — o filho já pode conter outros níveis. */
    private int linksBelow(String sku, int guard) {
        if (guard > MAX_PACKAGING_LINKS) {
            return guard;
        }
        int deepest = 0;
        for (SkuPackaging child : skuPackagingRepository.findByParent(sku)) {
            deepest = Math.max(deepest, 1 + linksBelow(child.childSku(), guard + 1));
        }
        return deepest;
    }

    /**
     * Só entra em embalagem o que tem saldo próprio na prateleira: variação, ou produto simples
     * sem variações. Kit não tem saldo; o produto base com variações também não (o estoque está
     * nas variações); e lote atravessando embalagem não tem regra definida — a quebra teria de
     * decidir de que lote do maço saíram os cigarros soltos.
     */
    private void requirePackageableSku(String sku) {
        Product product = productRepository.findByAnySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
        if (product.isKit()) {
            throw new InvalidPackagingException("kit não tem saldo próprio e não entra em embalagem: " + sku);
        }
        if (product.sku().equals(sku) && !product.variants().isEmpty()) {
            throw new InvalidPackagingException("o produto base " + sku
                    + " tem variações: ligue as variações (cor × embalagem), não a base");
        }
        if (product.lotTracked()) {
            throw new InvalidPackagingException("produto com controle de lote não entra em embalagem: " + sku);
        }
    }

    /**
     * Lote/validade só fazem sentido numa {@code ENTRADA} de SKU lote-rastreado (EST-F008):
     * {@code SAIDA} consome por FEFO automaticamente — o chamador não escolhe o lote — e
     * {@code AJUSTE} direto num SKU lote-rastreado é barrado à parte (ver
     * {@code requireActiveForInbound}-like check em bloco futuro). Ausência/presença fora do
     * esperado é rejeitada explicitamente, não ignorada silenciosamente — mesma régua que
     * {@code Pricing} usa para "nulo é desconhecido, não é ausência sem consequência".
     */
    private void validateLotInfo(String sku, MovementType type, boolean lotTracked, String lotCode,
            LocalDate expiryDate) {
        boolean lotInfoProvided = (lotCode != null && !lotCode.isBlank()) || expiryDate != null;
        if (lotInfoProvided && !lotTracked) {
            throw new UnexpectedLotInfoException(sku, "produto não é lote-rastreado");
        }
        if (lotInfoProvided && type != MovementType.ENTRADA) {
            throw new UnexpectedLotInfoException(sku, "lote só se aplica a ENTRADA — SAIDA consome por FEFO");
        }
        if (lotTracked && type == MovementType.ENTRADA
                && (lotCode == null || lotCode.isBlank() || expiryDate == null)) {
            throw new MissingLotInfoException(sku);
        }
    }

    /**
     * Custo unitário só se aplica a {@code ENTRADA} (EST-F007) — {@code SAIDA}/{@code AJUSTE} não
     * recalculam custo médio. Ausência é sempre válida (entrada sem custo conhecido); presença
     * fora de {@code ENTRADA} é rejeitada explicitamente, mesma régua de {@code validateLotInfo}.
     */
    private void validateUnitCost(String sku, MovementType type, BigDecimal unitCost) {
        if (unitCost != null && type != MovementType.ENTRADA) {
            throw new UnexpectedUnitCostException(sku, "custo só se aplica a ENTRADA");
        }
    }

    /**
     * Upsert no lote (EST-F008): soma a quantidade recebida, criando a linha se for a primeira
     * entrada daquele {@code lotCode}. A validade grava na criação e não muda depois — um
     * reabastecimento do mesmo lote informando outra validade é erro de digitação.
     */
    private void receiveIntoLot(String sku, Long warehouseId, String lotCode, LocalDate expiryDate,
            BigDecimal quantity) {
        StockLot lot = stockLotRepository.findBySkuAndWarehouseIdAndLotCode(sku, warehouseId, lotCode)
                .orElseGet(() -> StockLot.create(sku, warehouseId, lotCode, expiryDate));
        if (!lot.expiryDate().equals(expiryDate)) {
            throw new LotExpiryDateMismatchException(sku, lotCode, lot.expiryDate(), expiryDate);
        }
        stockLotRepository.save(lot.receive(quantity));
    }

    /**
     * Consome os lotes do par SKU/depósito por FEFO — do que vence primeiro em diante — até cobrir
     * {@code quantity}. Quem valida se existe saldo suficiente é {@code stock_balance}, já aplicado
     * antes desta chamada; se os lotes não cobrirem o total (descompasso entre o agregado e a soma
     * dos lotes — só possível por drift externo), consome o que existe e não lança erro: a venda já
     * foi validada contra o agregado, e o descompasso fica visível em
     * {@code GET /estoque/integrity/lot-mismatch} em vez de derrubar a operação.
     */
    private void consumeLotsFefo(String sku, Long warehouseId, BigDecimal quantity) {
        BigDecimal remaining = quantity;
        for (StockLot lot : stockLotRepository.findBySkuAndWarehouseId(sku, warehouseId)) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (lot.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal drawn = lot.quantity().min(remaining);
            stockLotRepository.save(lot.consume(drawn));
            remaining = remaining.subtract(drawn);
        }
    }

    /**
     * Explode a movimentação de um kit em uma por componente (EST-F015, §2.10) — venda e
     * estorno passam por aqui transparentemente, sem que {@code PdvService}/{@code OrderService}
     * precisem saber que o SKU vendido é um kit. Autoinvocação de {@link #adjustStock} dentro da
     * mesma classe não passa pelo proxy Spring, então continua na MESMA transação ambiente: a
     * venda/estorno do kit é atômica com o resto do pedido.
     */
    private StockBalance explodeKitMovement(Product kit, Warehouse warehouse, MovementType type,
            BigDecimal kitQuantity, String reason, String username) {
        if (type == MovementType.AJUSTE) {
            // Kit não tem saldo próprio nem contagem física própria — nada para ajustar
            // diretamente. Balanço de inventário deve contar os componentes.
            throw new KitDirectAdjustmentException(kit.sku());
        }
        List<KitComponent> recipe = kitComponentRepository.findByKitSku(kit.sku());
        if (recipe.isEmpty()) {
            throw new EmptyKitRecipeException(kit.sku());
        }
        String kitReason = reason + " (kit " + kit.sku() + ")";
        for (KitComponent component : recipe) {
            BigDecimal componentQuantity = component.quantity().multiply(kitQuantity);
            adjustStock(component.componentSku(), warehouse.code(), type, componentQuantity, kitReason, username);
        }
        // Kit não tem linha própria em stock_balance: devolve o saldo derivado recalculado,
        // nunca o de um componente qualquer (seria a unidade errada).
        return derivedKitBalance(kit, warehouse.id());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockMovement> listMovements(String sku, String warehouseCode, MovementType type,
            Instant from, Instant to, int page, int size) {
        Long warehouseId = warehouseCode == null ? null : requireWarehouse(warehouseCode).id();
        return stockMovementRepository.findBySkuAndWarehouseId(sku, warehouseId, type, from, to, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockMovement> listPurchaseHistory(String sku, String warehouseCode, int page, int size) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        return stockMovementRepository.findEntradasBySkuAndWarehouseId(sku, warehouse.id(), page, size);
    }

    @Override
    @Transactional
    public void setReorderPoint(String sku, String warehouseCode, BigDecimal minQuantity) {
        requireKnownSku(sku);
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        Long existingId = reorderPointRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .map(ReorderPoint::id)
                .orElse(null);
        reorderPointRepository.save(new ReorderPoint(existingId, sku, warehouse.id(), minQuantity));
    }

    @Override
    @Transactional
    public void deleteReorderPoint(String sku, String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        reorderPointRepository.deleteBySkuAndWarehouseId(sku, warehouse.id());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReorderPoint> getReorderPoint(String sku, String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        return reorderPointRepository.findBySkuAndWarehouseId(sku, warehouse.id());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ReorderPoint> listReorderPoints(String warehouseCode, int page, int size) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        return reorderPointRepository.findByWarehouseId(warehouse.id(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrphanSku> listOrphanSkus(int page, int size) {
        return stockIntegrityRepository.findOrphanSkus(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ReservationIntegrityMismatch> listReservationMismatches(int page, int size) {
        return stockIntegrityRepository.findReservationMismatches(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LotIntegrityMismatch> listLotMismatches(int page, int size) {
        return stockIntegrityRepository.findLotMismatches(page, size);
    }

    // ---------------------------------------------------------------------------------------
    // Balanço de inventário (EST-F006)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public StockCount openStockCount(String warehouseCode, String username) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        stockCountRepository.findOpenByWarehouseId(warehouse.id()).ifPresent(open -> {
            throw new StockCountAlreadyOpenException(warehouseCode, open.id());
        });
        return stockCountRepository.save(StockCount.open(warehouse.id(), username));
    }

    @Override
    @Transactional
    public StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity) {
        return recordCountedItem(stockCountId, sku, countedQuantity, null);
    }

    @Override
    @Transactional
    public StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity, String lotCode) {
        StockCount count = requireOpenStockCount(stockCountId);
        // Mesma pré-condição de adjustStock (EST-C002): não adianta contar um SKU que o
        // fechamento não conseguiria ajustar.
        requireKnownSku(sku);
        Optional<Product> product = productRepository.findByAnySku(sku);
        // Kit não tem contagem física própria (EST-F015) — rejeitar aqui, na hora do registro,
        // em vez de deixar o erro só aparecer confusamente quando closeStockCount tentar um
        // AJUSTE direto no kit e abortar o fechamento inteiro.
        product.filter(Product::isKit).ifPresent(kit -> { throw new KitDirectAdjustmentException(sku); });

        // EST-F008: SKU lote-rastreado é contado por lote, não agregado — mesma régua condicional
        // de validateLotInfo, mas aqui não há tipo de movimento: a contagem em si já é a operação.
        boolean lotTracked = product.map(Product::lotTracked).orElse(false);
        boolean lotInfoProvided = lotCode != null && !lotCode.isBlank();
        if (lotTracked && !lotInfoProvided) {
            throw new MissingLotInfoException(sku);
        }
        if (!lotTracked && lotInfoProvided) {
            throw new UnexpectedLotInfoException(sku, "produto não é lote-rastreado");
        }
        // EST-C0xx: o saldo esperado é carimbado AGORA, no registro — não recalculado no
        // fechamento. É o retrato do saldo do sistema no instante em que o contador viu a
        // prateleira; comparar contra ele (não contra o saldo do fechamento) é o que permite ao
        // fechamento aplicar só a divergência de verdade, sem apagar movimentação legítima que
        // aconteça depois da contagem e antes do balanço fechar.
        BigDecimal systemQuantity;
        if (lotInfoProvided) {
            StockLot lot = stockLotRepository.findBySkuAndWarehouseIdAndLotCode(sku, count.warehouseId(), lotCode)
                    .orElseThrow(() -> new StockLotNotFoundException(sku, lotCode));
            systemQuantity = lot.quantity();
        } else {
            systemQuantity = stockBalanceRepository.findBySkuAndWarehouseId(sku, count.warehouseId())
                    .map(StockBalance::quantity)
                    .orElse(BigDecimal.ZERO);
        }
        StockCount counted = count.withCountedItem(sku, countedQuantity, lotCode)
                .withReconciledItem(sku, lotCode, systemQuantity);
        return stockCountRepository.save(counted);
    }

    @Override
    @Transactional
    public StockCount closeStockCount(Long stockCountId, String username) {
        StockCount count = requireOpenStockCount(stockCountId);
        Warehouse warehouse = getWarehouse(count.warehouseId());

        Map<String, List<StockCountItem>> bySku = new LinkedHashMap<>();
        count.items().forEach(item -> bySku.computeIfAbsent(item.sku(), k -> new ArrayList<>()).add(item));

        List<StockCountItem> reconciled = new ArrayList<>();
        for (Map.Entry<String, List<StockCountItem>> entry : bySku.entrySet()) {
            String sku = entry.getKey();
            List<StockCountItem> items = entry.getValue();
            boolean lotTracked = items.stream().anyMatch(i -> i.lotCode() != null);
            if (lotTracked) {
                reconciled.addAll(closeLotTrackedSku(stockCountId, warehouse, sku, items, username));
            } else {
                reconciled.add(closeAggregateSku(stockCountId, warehouse, items.get(0), username));
            }
        }
        return stockCountRepository.save(count.withReconciledItems(reconciled).closed());
    }

    /**
     * Fechamento de um SKU não lote-rastreado: confronta contra o agregado de
     * {@code stock_balance}, como sempre foi. Contagem que bateu não vira movimentação — um AJUSTE
     * de saldo para ele mesmo só faria ruído no ledger.
     *
     * <p>{@code item} já chega reconciliado desde {@code recordCountedItem} — {@code
     * expectedQuantity}/{@code difference} são o retrato do saldo no instante da CONTAGEM, não
     * deste fechamento. Por isso o AJUSTE aplicado aqui soma {@code item.difference()} ao saldo
     * ATUAL (lido agora), em vez de substituir o saldo pelo valor contado direto: se uma venda
     * aconteceu entre a contagem e o fechamento, ela continua refletida — só a divergência que a
     * contagem de fato encontrou é corrigida.</p>
     */
    private StockCountItem closeAggregateSku(Long stockCountId, Warehouse warehouse, StockCountItem item,
            String username) {
        if (item.diverges()) {
            BigDecimal currentSystemQuantity = stockBalanceRepository
                    .findBySkuAndWarehouseId(item.sku(), warehouse.id())
                    .map(StockBalance::quantity)
                    .orElse(BigDecimal.ZERO);
            BigDecimal target = currentSystemQuantity.add(item.difference());
            adjustStock(item.sku(), warehouse.code(), MovementType.AJUSTE, target,
                    "Balanço de inventário #" + stockCountId, username);
        }
        return item;
    }

    /**
     * Fechamento de um SKU lote-rastreado (EST-F008): cada lote contado é confrontado e
     * reconciliado contra o próprio {@code StockLot}, não contra o agregado — é isto que mantém
     * {@code SUM(stock_lot.quantity) == stock_balance.quantity} depois de um balanço, em vez de
     * deixar a soma dos lotes derivar silenciosamente do agregado (o gap que {@code
     * StockLot.reconciledTo} existia para fechar e ninguém chamava). Só depois de reconciliar todo
     * lote do grupo é que o agregado recebe UM AJUSTE para a soma — mesmo formato de ledger que o
     * SKU não lote-rastreado, um evento por SKU por fechamento, não um por lote.
     */
    private List<StockCountItem> closeLotTrackedSku(Long stockCountId, Warehouse warehouse, String sku,
            List<StockCountItem> items, String username) {
        List<StockCountItem> reconciled = new ArrayList<>();
        BigDecimal newTotal = BigDecimal.ZERO;
        for (StockCountItem item : items) {
            // Mesmo raciocínio de closeAggregateSku: item já reconciliado no registro, o ajuste
            // soma a divergência encontrada à quantidade ATUAL do lote, não substitui pelo valor
            // contado — uma consumição FEFO entre a contagem e o fechamento não é apagada.
            if (item.diverges()) {
                StockLot lot = stockLotRepository
                        .findBySkuAndWarehouseIdAndLotCode(sku, warehouse.id(), item.lotCode())
                        .orElseThrow(() -> new StockLotNotFoundException(sku, item.lotCode()));
                BigDecimal target = lot.quantity().add(item.difference());
                stockLotRepository.save(lot.reconciledTo(target));
            }
            reconciled.add(item);
            newTotal = newTotal.add(item.countedQuantity());
        }

        // Este confronto do agregado contra a SOMA dos lotes contados continua lido no fechamento
        // (não tem um "instante da contagem" único: cada lote pode ter sido contado em momento
        // diferente). Ainda sujeito à mesma classe de corrida que motivou o fix acima, só que numa
        // janela menor (entre o último lote contado e o fechamento) — gap residual conhecido, não
        // corrigido nesta rodada.
        BigDecimal systemQuantity = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .map(StockBalance::quantity)
                .orElse(BigDecimal.ZERO);
        if (newTotal.compareTo(systemQuantity) != 0) {
            adjustStock(sku, warehouse.code(), MovementType.AJUSTE, newTotal,
                    "Balanço de inventário #" + stockCountId, username);
        }
        return reconciled;
    }

    @Override
    @Transactional
    public StockCount cancelStockCount(Long stockCountId) {
        return stockCountRepository.save(requireOpenStockCount(stockCountId).cancelled());
    }

    @Override
    @Transactional(readOnly = true)
    public StockCount getStockCount(Long stockCountId) {
        return stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new StockCountNotFoundException(stockCountId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockCount> listStockCounts(String warehouseCode, int page, int size) {
        return stockCountRepository.findByWarehouseId(requireWarehouse(warehouseCode).id(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Warehouse getWarehouse(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundException(String.valueOf(warehouseId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Warehouse getWarehouseByCode(String code) {
        return requireWarehouse(code);
    }

    @Override
    @Transactional(readOnly = true)
    public Warehouse getDefaultWarehouse() {
        String code = systemConfigPort.findByKey(DEFAULT_WAREHOUSE_CONFIG_KEY)
                .map(SystemConfig::value)
                .filter(value -> !value.isBlank())
                .orElseThrow(DefaultWarehouseNotConfiguredException::new);
        return requireWarehouse(code);
    }

    // ---------------------------------------------------------------------------------------
    // Reserva de estoque (EST-F021)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public StockReservation reserveStock(String sku, String warehouseCode, BigDecimal quantity,
            String ownerReference, Duration ttl, String username) {
        // Mesmas pré-condições de adjustStock (EST-C002): não adianta reservar um SKU que a baixa
        // depois não conseguiria movimentar.
        requireKnownSku(sku);
        Warehouse warehouse = requireWarehouse(warehouseCode);

        Map<String, BigDecimal> kitBuildableBefore = kitBuildableSnapshot(sku, warehouse.id());

        StockBalance current = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
        // A escrita do contador é o que serializa: duas reservas concorrentes do mesmo SKU disputam
        // esta linha e uma delas sai em 409, em vez de as duas passarem e o disponível ficar negativo.
        StockBalance reserved = stockBalanceRepository.save(current.reserve(quantity));

        Duration effectiveTtl = ttl == null ? defaultReservationTtl : ttl;
        StockReservation reservation = stockReservationRepository.save(StockReservation.create(
                sku, warehouse.id(), quantity, ownerReference,
                Instant.now().plus(effectiveTtl), username));

        // O alerta de reposição olha o disponível, não o físico: quando um pedido online segura a
        // última unidade, é nesse instante que se precisa repor — não quando ela for despachada.
        // Mesmo raciocínio vale para o kit ficar sem estoque de componente (Bloco 1.2).
        notifyIfBelowReorderPoint(reserved);
        notifyIfKitsNewlyBlocked(kitBuildableBefore, warehouse.id());
        return reservation;
    }

    @Override
    @Transactional
    public StockReservation consumeReservation(Long reservationId, String username) {
        StockReservation reservation = requireActiveReservation(reservationId);
        Warehouse warehouse = getWarehouse(reservation.warehouseId());

        Map<String, BigDecimal> kitBuildableBefore = kitBuildableSnapshot(reservation.sku(), warehouse.id());

        StockBalance current = stockBalanceRepository.findBySkuAndWarehouseId(
                        reservation.sku(), reservation.warehouseId())
                .orElseGet(() -> StockBalance.zero(reservation.sku(), reservation.warehouseId()));

        // Não passa por apply(SAIDA): aquele caminho valida contra o disponível, e o disponível
        // aqui já está descontado desde a reserva — validá-lo de novo recusaria a própria reserva
        // que estamos consumindo. O físico e o reservado caem juntos.
        StockBalance updated = stockBalanceRepository.save(current.consumeReservation(reservation.quantity()));

        // A mercadoria saiu de verdade: o ledger tem que registrar, com o mesmo formato das outras
        // saídas, senão o histórico do SKU fica com um buraco do tamanho das vendas online.
        stockMovementRepository.save(StockMovement.create(reservation.sku(), reservation.warehouseId(),
                MovementType.SAIDA, reservation.quantity(),
                "Consumo da reserva #" + reservationId + " (" + reservation.ownerReference() + ")", username));

        // EST-F008: a mercadoria reservada também sai de um lote de verdade quando o SKU é
        // lote-rastreado — este caminho não passa por adjustStock, então o FEFO precisa ser
        // disparado aqui também, senão stock_lot nunca desconta a venda do marketplace.
        productRepository.findByAnySku(reservation.sku())
                .filter(Product::lotTracked)
                .ifPresent(p -> consumeLotsFefo(reservation.sku(), reservation.warehouseId(), reservation.quantity()));

        notifyIfBelowReorderPoint(updated);
        notifyIfKitsNewlyBlocked(kitBuildableBefore, warehouse.id());
        return stockReservationRepository.save(reservation.consumed());
    }

    @Override
    @Transactional
    public StockReservation releaseReservation(Long reservationId, String username) {
        return stockReservationRepository.save(releaseInternal(requireActiveReservation(reservationId)));
    }

    @Override
    @Transactional
    public int releaseReservationsByOwner(String ownerReference, String username) {
        List<StockReservation> active = stockReservationRepository.findActiveByOwnerReference(ownerReference);
        active.forEach(reservation -> stockReservationRepository.save(releaseInternal(reservation)));
        return active.size();
    }

    @Override
    @Transactional
    public int consumeReservationsByOwner(String ownerReference, String username) {
        List<StockReservation> active = stockReservationRepository.findActiveByOwnerReference(ownerReference);
        active.forEach(reservation -> consumeReservation(reservation.id(), username));
        return active.size();
    }

    @Override
    @Transactional(readOnly = true)
    public StockReservation getStockReservation(Long reservationId) {
        return stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new StockReservationNotFoundException(reservationId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockReservation> listReservations(String sku, String warehouseCode,
            ReservationStatus status, int page, int size) {
        Long warehouseId = warehouseCode == null ? null : requireWarehouse(warehouseCode).id();
        return stockReservationRepository.findByFilters(sku, warehouseId, status, page, size);
    }

    @Override
    @Transactional
    public int expireReservations(int batchSize) {
        List<StockReservation> expired = stockReservationRepository.findExpired(Instant.now(), batchSize);
        for (StockReservation reservation : expired) {
            devolveReservedQuantity(reservation);
            stockReservationRepository.save(reservation.expired());
        }
        return expired.size();
    }

    @Override
    @Transactional
    public int alertExpiringLots(int cutoffDays, int batchSize) {
        List<StockLot> expiring = stockLotRepository.findExpiringSoon(LocalDate.now().plusDays(cutoffDays), batchSize);
        if (expiring.isEmpty()) {
            return 0;
        }
        dispatchLotExpiryAlerts(expiring);
        expiring.forEach(lot -> stockLotRepository.save(lot.alerted()));
        return expiring.size();
    }

    /**
     * Uma notificação por passada do varredor, não uma por lote — quem recebe não precisa de N
     * notificações separadas para N lotes vencendo na mesma manhã. Mesmo formato de
     * {@link #dispatchReorderAlerts}, mas sem passar pelo {@code afterCommitExecutor}: aquele
     * existe para agrupar chamadas que acontecem no meio de uma transação de negócio maior (venda,
     * reserva) e só disparar depois do commit; aqui o próprio método já É a transação de topo do
     * varredor, não há nada maior para esperar committar.
     */
    private void dispatchLotExpiryAlerts(List<StockLot> lots) {
        String title = "Lote de estoque vencendo";
        StringBuilder body = new StringBuilder(lots.size() == 1
                ? "O lote a seguir está vencendo:"
                : lots.size() + " lotes estão vencendo:");
        lots.forEach(lot -> body.append("\n- ").append(lot.sku())
                .append(" (lote ").append(lot.lotCode()).append("): vence em ").append(lot.expiryDate())
                .append(", saldo ").append(lot.quantity()));

        userRepository.findUsernamesByPermission(STOCK_MANAGE_PERMISSION)
                .forEach(username -> notificationUseCase.notify(username, NotificationType.SYSTEM,
                        title, body.toString()));
    }

    // ---------------------------------------------------------------------------------------
    // Kits (EST-F015) — virtuais, de um nível só (§2.10 do plano)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public Product defineKitRecipe(String kitSku, List<KitComponentCommand> components) {
        // findBySku, não findByAnySku: kit é sempre SKU pai, nunca uma variação.
        Product kit = productRepository.findBySku(kitSku)
                .orElseThrow(() -> new ProductNotFoundException(kitSku));
        if (components == null || components.isEmpty()) {
            throw new EmptyKitRecipeException(kitSku);
        }
        // Kit e variações são mutuamente exclusivos: não há endpoint para adicionar variação
        // depois da criação (ver Product.withDetails), então checar aqui, na promoção, fecha o
        // espaço todo com uma linha só.
        if (!kit.variants().isEmpty()) {
            throw new KitHasVariantsException(kitSku);
        }
        // Sustenta a invariante de um nível só contra a porta dos fundos: sem isto, promover a
        // KIT um SKU que já é componente de outro kit criaria kit-dentro-de-kit sem nunca passar
        // pela checagem "componente precisa ser SIMPLES", que só roda no sentido contrário.
        if (kitComponentRepository.isUsedAsComponent(kitSku)) {
            throw new KitComponentAlreadyInUseException(kitSku);
        }

        List<KitComponent> recipe = validateAndBuildComponents(kitSku, components);

        kitComponentRepository.replaceRecipe(kitSku, recipe);
        return productRepository.save(kit.withType(ProductType.KIT));
    }

    /**
     * Valida cada linha da receita e monta a lista de {@link KitComponent} — reaproveitado tanto
     * por {@link #defineKitRecipe} (promoção via PUT) quanto pela criação atômica de kit em
     * {@link #createProduct}. Não valida receita vazia nem invariantes do PRÓPRIO kit (variações,
     * já-é-componente) — isso cada chamador decide no seu contexto, porque um kit recém-criado
     * nunca tem variações e nunca pode já ser componente de outro (SKU não existia até agora).
     */
    private List<KitComponent> validateAndBuildComponents(String kitSku, List<KitComponentCommand> components) {
        Set<String> seen = new LinkedHashSet<>();
        List<KitComponent> recipe = new ArrayList<>();
        for (KitComponentCommand command : components) {
            String componentSku = command.componentSku();
            if (componentSku.equals(kitSku)) {
                throw new KitSelfReferenceException(kitSku);
            }
            if (!seen.add(componentSku)) {
                throw new DuplicateKitComponentException(kitSku, componentSku);
            }
            Product component = productRepository.findByAnySku(componentSku)
                    .orElseThrow(() -> new ProductNotFoundException(componentSku));
            if (component.isKit()) {
                throw new KitComponentNotSimpleException(kitSku, componentSku, component.type());
            }
            if (!component.kitComponentEligible()) {
                throw new KitComponentNotEligibleException(kitSku, componentSku);
            }
            if (!component.active()) {
                throw new KitComponentInactiveException(kitSku, componentSku);
            }
            recipe.add(KitComponent.create(kitSku, componentSku, command.quantity()));
        }
        return recipe;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitComponent> getKitRecipe(String kitSku) {
        // Confirma que o SKU existe antes de devolver lista vazia — SKU desconhecido é 404, SKU
        // que existe mas nunca foi kit é lista vazia. Duas coisas diferentes.
        productRepository.findBySku(kitSku).orElseThrow(() -> new ProductNotFoundException(kitSku));
        return kitComponentRepository.findByKitSku(kitSku);
    }

    /**
     * Cruza {@link #getKitRecipe} com o catálogo para trazer nome/imagem/preço/status de cada
     * componente (Bloco 3.3) — defensivo contra componente ausente (não deveria acontecer, já que
     * {@code defineKitRecipe}/{@code createProduct} validam existência antes de gravar, mas segue
     * o mesmo estilo defensivo de {@link #derivedKitBalance}).
     */
    @Override
    @Transactional(readOnly = true)
    public List<KitComponentDetail> getKitRecipeDetailed(String kitSku) {
        return getKitRecipe(kitSku).stream().map(component -> {
            Product componentProduct = productRepository.findByAnySku(component.componentSku()).orElse(null);
            return new KitComponentDetail(component.componentSku(), component.quantity(),
                    componentProduct == null ? null : componentProduct.name(),
                    componentProduct == null ? null : componentProduct.imageUrl(),
                    componentProduct == null ? null : componentProduct.pricing().salePrice(),
                    componentProduct != null && componentProduct.active());
        }).toList();
    }

    @Override
    @Transactional
    public Product clearKitRecipe(String kitSku) {
        Product kit = productRepository.findBySku(kitSku).orElseThrow(() -> new ProductNotFoundException(kitSku));
        if (!kit.isKit()) {
            return kit;
        }
        kitComponentRepository.replaceRecipe(kitSku, List.of());
        return productRepository.save(kit.withType(ProductType.SIMPLES));
    }

    private StockReservation releaseInternal(StockReservation reservation) {
        devolveReservedQuantity(reservation);
        return reservation.released();
    }

    /**
     * Devolve a quantidade ao disponível sem tocar no físico — nada entrou nem saiu da prateleira,
     * então <b>não</b> gera {@link StockMovement}. Registrar uma movimentação aqui inflaria o ledger
     * com entradas de mercadoria que nunca se moveu.
     */
    private void devolveReservedQuantity(StockReservation reservation) {
        stockBalanceRepository.findBySkuAndWarehouseId(reservation.sku(), reservation.warehouseId())
                .ifPresent(balance -> stockBalanceRepository.save(
                        balance.releaseReservation(reservation.quantity())));
    }

    private StockReservation requireActiveReservation(Long reservationId) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new StockReservationNotFoundException(reservationId));
        if (!reservation.isActive()) {
            throw new StockReservationNotActiveException(reservationId, reservation.status());
        }
        return reservation;
    }

    private Warehouse requireWarehouse(String warehouseCode) {
        return warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
    }

    private StockCount requireOpenStockCount(Long stockCountId) {
        StockCount count = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new StockCountNotFoundException(stockCountId));
        if (!count.isOpen()) {
            throw new StockCountNotOpenException(stockCountId, count.status());
        }
        return count;
    }

    /**
     * Barra SKU que não existe no catálogo antes de qualquer escrita. Sem isso — e não há FK de
     * {@code stock_balance}/{@code stock_movement} para {@code product} — um SKU digitado errado
     * vindo do PDV ou de Compras cria saldo e ledger órfãos silenciosamente.
     */
    private void requireKnownSku(String sku) {
        if (!productRepository.existsBySku(sku)) {
            throw new ProductNotFoundException(sku);
        }
    }

    /**
     * Produto ou depósito desativado recusa <b>entrada</b> de estoque (EST-F018), seja ela manual
     * ou vinda de um recebimento de Compras.
     *
     * <p>Só {@code ENTRADA} é barrada. {@code SAIDA} continua livre de propósito — desativar
     * significa "não reponho mais", e bloquear a saída deixaria preso o saldo que ainda existe na
     * prateleira. {@code AJUSTE} também passa: é o caminho de correção de inventário, e um
     * produto desativado com contagem errada precisa poder ser acertado.</p>
     */
    private void requireActiveForInbound(String sku, Warehouse warehouse, MovementType type) {
        if (type != MovementType.ENTRADA) {
            return;
        }
        if (!warehouse.active()) {
            throw new InactiveWarehouseException(warehouse.code());
        }
        if (!productRepository.isSkuActive(sku)) {
            throw new InactiveProductException(sku);
        }
    }

    /**
     * Acumula o alerta em vez de enviá-lo na hora. Uma venda que derruba N SKUs abaixo do mínimo
     * registra N alertas, mas gera <b>uma</b> notificação por destinatário, despachada depois do
     * commit — nem a transação de venda espera o envio, nem alguém é avisado sobre uma venda que
     * acabou revertida.
     */
    private void notifyIfBelowReorderPoint(StockBalance balance) {
        // Compara contra o DISPONÍVEL, não o físico (EST-F021): unidade reservada para um pedido
        // online não está à venda, e esperar o despacho para avisar atrasaria a reposição em todo o
        // tempo de separação. Enquanto não houver reserva, disponível == físico e o comportamento
        // anterior fica idêntico.
        BigDecimal available = balance.availableQuantity();
        reorderPointRepository.findBySkuAndWarehouseId(balance.sku(), balance.warehouseId())
                .filter(reorderPoint -> reorderPoint.isBelow(available))
                .ifPresent(reorderPoint -> afterCommitExecutor.accumulate(REORDER_ALERT_BATCH,
                        new ReorderAlert(balance.sku(), available, reorderPoint.minQuantity()),
                        this::dispatchReorderAlerts));
    }

    private void dispatchReorderAlerts(List<ReorderAlert> alerts) {
        // O mesmo SKU pode aparecer mais de uma vez na operação (dois itens do mesmo produto na
        // venda); vale o último saldo observado.
        Map<String, ReorderAlert> bySku = new LinkedHashMap<>();
        alerts.forEach(alert -> bySku.put(alert.sku(), alert));

        String title = "Estoque abaixo do ponto de reposição";
        StringBuilder body = new StringBuilder(bySku.size() == 1
                ? "O SKU a seguir está abaixo do ponto de reposição:"
                : bySku.size() + " SKUs estão abaixo do ponto de reposição:");
        bySku.values().forEach(alert -> body.append("\n- ").append(alert.sku())
                .append(": saldo ").append(alert.quantity())
                .append(", mínimo ").append(alert.minQuantity()));

        userRepository.findUsernamesByPermission(STOCK_MANAGE_PERMISSION)
                .forEach(username -> notificationUseCase.notify(username, NotificationType.SYSTEM,
                        title, body.toString()));
        emailReorderAlerts(bySku.values());
        publishReorderEvents(bySku.values());
    }

    /**
     * Um {@code STOCK_BELOW_REORDER_POINT} por SKU — dispara as automações de ESTOQUE_BAIXO. Já
     * estamos depois do commit (callback do {@code afterCommitExecutor}).
     */
    private void publishReorderEvents(Collection<ReorderAlert> alerts) {
        alerts.forEach(alert -> {
            try {
                auditEvents.publish(AuditEvent.of(AuditEvent.EventType.STOCK_BELOW_REORDER_POINT, "system", Map.of(
                        "sku", alert.sku(),
                        "disponivel", alert.quantity(),
                        "minimo", alert.minQuantity())));
            } catch (Exception ex) {
                log.warn("estoque.reorder-alert.event.failed sku={} error={}", alert.sku(), ex.getMessage());
            }
        });
    }

    /**
     * O mesmo lote de alertas, por e-mail, para quem cuida do estoque — um e-mail por operação, não
     * por SKU. Falha no e-mail não pode desfazer o aviso in-app, que já saiu.
     */
    private void emailReorderAlerts(Collection<ReorderAlert> alerts) {
        try {
            List<NotificationEmail.Row> rows = alerts.stream()
                    .map(alert -> productRepository.findBySku(alert.sku())
                            .map(product -> product.name() + " (" + alert.sku() + ")")
                            .map(label -> NotificationEmail.Row.of(label, reorderValue(alert)))
                            .orElseGet(() -> NotificationEmail.Row.of(alert.sku(), reorderValue(alert))))
                    .toList();
            String subject = rows.size() == 1
                    ? rows.get(0).label() + " chegou ao ponto de reposição"
                    : rows.size() + " produtos chegaram ao ponto de reposição";
            managerNotifications.emailPermission(STOCK_MANAGE_PERMISSION, NotificationType.ESTOQUE,
                    NotificationEmail.builder("estoque.reposicao", subject)
                            .tone(NotificationEmail.Tone.WARNING)
                            .intro("O disponível ficou abaixo do mínimo cadastrado. Hora de repor.")
                            .section("Repor", rows)
                            .action("Abrir estoque", "/app/estoque")
                            .build());
        } catch (Exception ex) {
            log.warn("estoque.reorder-alert.email.failed error={}", ex.getMessage());
        }
    }

    private static String reorderValue(ReorderAlert alert) {
        return "disponível " + alert.quantity().stripTrailingZeros().toPlainString()
                + " · mínimo " + alert.minQuantity().stripTrailingZeros().toPlainString();
    }

    /**
     * Fotografa o {@code buildableQuantity} de todo kit que usa {@code componentSku} na receita,
     * ANTES de uma mutação de saldo (Bloco 1.2) — só para esses kits, não o catálogo inteiro, para
     * o custo desta checagem ficar proporcional a "quantos kits dependem deste SKU", não a
     * "quantos kits existem".
     */
    private Map<String, BigDecimal> kitBuildableSnapshot(String componentSku, Long warehouseId) {
        List<String> kitSkus = kitComponentRepository.findKitSkusByComponentSku(componentSku);
        if (kitSkus.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> snapshot = new LinkedHashMap<>();
        for (String kitSku : kitSkus) {
            productRepository.findBySku(kitSku)
                    .ifPresent(kit -> snapshot.put(kitSku, derivedKitBalance(kit, warehouseId).quantity()));
        }
        return snapshot;
    }

    /**
     * Compara o snapshot capturado por {@link #kitBuildableSnapshot} (ANTES da mutação) contra o
     * {@code buildableQuantity} ATUAL (depois de salvo) e notifica só quem transicionou de
     * {@code >0} para {@code 0} — não em toda movimentação, senão o sino de notificações vira
     * ruído (Bloco 1.2).
     */
    private void notifyIfKitsNewlyBlocked(Map<String, BigDecimal> buildableBefore, Long warehouseId) {
        buildableBefore.forEach((kitSku, before) -> {
            if (before.signum() <= 0) {
                return;
            }
            productRepository.findBySku(kitSku).ifPresent(kit -> {
                BigDecimal after = derivedKitBalance(kit, warehouseId).quantity();
                if (after.signum() == 0) {
                    afterCommitExecutor.accumulate(KIT_BLOCKED_ALERT_BATCH,
                            new KitBlockedAlert(kitSku, kit.name()), this::dispatchKitBlockedAlerts);
                }
            });
        });
    }

    private void dispatchKitBlockedAlerts(List<KitBlockedAlert> alerts) {
        Map<String, KitBlockedAlert> byKit = new LinkedHashMap<>();
        alerts.forEach(alert -> byKit.put(alert.kitSku(), alert));

        String title = byKit.size() == 1 ? "Kit sem componente disponível"
                : byKit.size() + " kits sem componente disponível";
        StringBuilder body = new StringBuilder(byKit.size() == 1
                ? "O kit a seguir não pode ser montado no momento:"
                : "Os kits a seguir não podem ser montados no momento:");
        byKit.values().forEach(alert -> body.append("\n- ").append(alert.kitName())
                .append(" (").append(alert.kitSku()).append(")"));

        String targetUrl = byKit.size() == 1
                ? "/app/estoque/kits/" + byKit.values().iterator().next().kitSku()
                : "/app/estoque/kits?blocked=true";

        userRepository.findUsernamesByPermission(STOCK_MANAGE_PERMISSION)
                .forEach(username -> notificationUseCase.notify(username, NotificationType.ESTOQUE,
                        title, body.toString(), targetUrl));
    }
}
