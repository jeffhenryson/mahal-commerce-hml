package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.BrandDTOConverter;
import com.cernecommerce.adapter.in.converter.CategoryDTOConverter;
import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.converter.ReplenishmentListDTOConverter;
import com.cernecommerce.adapter.in.converter.StockCountDTOConverter;
import com.cernecommerce.adapter.in.converter.StockMovementDTOConverter;
import com.cernecommerce.adapter.in.converter.StockReservationDTOConverter;
import com.cernecommerce.adapter.in.converter.WarehouseDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ActiveRequest;
import com.cernecommerce.adapter.in.dtos.request.AddVariantsRequest;
import com.cernecommerce.adapter.in.dtos.request.AttributeTypeRequest;
import com.cernecommerce.adapter.in.dtos.request.ChangeSkuRequest;
import com.cernecommerce.adapter.in.dtos.request.BrandPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.BrandRequest;
import com.cernecommerce.adapter.in.dtos.request.CategoryPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ReplenishmentItemPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ReplenishmentItemRequest;
import com.cernecommerce.adapter.in.dtos.request.CategoryRequest;
import com.cernecommerce.adapter.in.dtos.request.LotTrackedRequest;
import com.cernecommerce.adapter.in.dtos.request.InitialStockRequest;
import com.cernecommerce.adapter.in.dtos.request.KitRecipeRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductVariantPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ReorderPointRequest;
import com.cernecommerce.adapter.in.dtos.request.StockCountItemRequest;
import com.cernecommerce.adapter.in.dtos.request.StockCountRequest;
import com.cernecommerce.adapter.in.dtos.request.StockMovementRequest;
import com.cernecommerce.adapter.in.dtos.request.RegisterOpenPackageRequest;
import com.cernecommerce.adapter.in.dtos.request.DefinePackagingRequest;
import com.cernecommerce.adapter.in.dtos.request.ParentSellableRequest;
import com.cernecommerce.adapter.in.dtos.response.PackagingLevelResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PackagingResponseDTO;
import com.cernecommerce.core.domain.model.estoque.SkuPackaging;
import com.cernecommerce.adapter.in.dtos.request.ReplaceOpenPackageRequest;
import com.cernecommerce.adapter.in.dtos.request.StockConversionRequest;
import com.cernecommerce.adapter.in.dtos.request.WarehousePatchRequest;
import com.cernecommerce.adapter.in.dtos.request.WarehouseRequest;
import com.cernecommerce.adapter.in.dtos.response.BrandResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CategoryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.EstoqueSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitAvailabilityResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitComponentResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.LotIntegrityMismatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrphanSkuResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PricingResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PurchaseHistoryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ReplenishmentListItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ReorderPointResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ReservationIntegrityMismatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockBalanceResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.AbcEntryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OpenPackageResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockConversionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockCountResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockLotResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockMovementResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockReservationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.WarehouseResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.AttributeType;
import com.cernecommerce.core.domain.model.estoque.Brand;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.KitAvailability;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.exception.storage.InvalidImageFormatException;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.ProductImageUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Grade de produtos e controle de saldo multi-depósito do domínio <b>estoque</b>: cadastro de
 * SKU pai com variações (sabor/tamanho/cor), depósitos (loja física/e-commerce) e consulta de saldo.
 */
@RestController
@RequestMapping("/estoque")
@Tag(name = "Estoque", description = "Grade de produtos e inventário")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class EstoqueController {

    private final EstoqueUseCase estoqueUseCase;
    private final ProductDTOConverter converter;
    private final WarehouseDTOConverter warehouseConverter;
    private final StockMovementDTOConverter movementConverter;
    private final StockCountDTOConverter stockCountConverter;
    private final StockReservationDTOConverter reservationConverter;
    private final CategoryDTOConverter categoryConverter;
    private final BrandDTOConverter brandConverter;
    private final ProductImageUseCase productImageUseCase;
    private final ApplicationEventPublisher publisher;
    private final ComprasUseCase comprasUseCase;
    private final ReplenishmentListDTOConverter replenishmentListConverter;

    public EstoqueController(EstoqueUseCase estoqueUseCase, ProductDTOConverter converter,
            WarehouseDTOConverter warehouseConverter, StockMovementDTOConverter movementConverter,
            StockCountDTOConverter stockCountConverter, StockReservationDTOConverter reservationConverter,
            CategoryDTOConverter categoryConverter, BrandDTOConverter brandConverter,
            ProductImageUseCase productImageUseCase, ApplicationEventPublisher publisher,
            ComprasUseCase comprasUseCase, ReplenishmentListDTOConverter replenishmentListConverter) {
        this.estoqueUseCase = estoqueUseCase;
        this.converter = converter;
        this.warehouseConverter = warehouseConverter;
        this.movementConverter = movementConverter;
        this.stockCountConverter = stockCountConverter;
        this.reservationConverter = reservationConverter;
        this.categoryConverter = categoryConverter;
        this.brandConverter = brandConverter;
        this.productImageUseCase = productImageUseCase;
        this.publisher = publisher;
        this.comprasUseCase = comprasUseCase;
        this.replenishmentListConverter = replenishmentListConverter;
    }

    @Operation(summary = "Lista produtos paginados, com busca, filtros e ordenação",
            description = "Todos os filtros são opcionais e combináveis; omitir todos devolve o "
                    + "catálogo inteiro paginado, como antes. `search` casa por trecho em nome "
                    + "OU SKU, sem diferenciar maiúsculas. `category` e `brand` são igualdade "
                    + "exata (também sem diferenciar maiúsculas). A ordenação sempre desempata "
                    + "por id, para a paginação ser estável.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Parâmetro fora da faixa ou valor de enum inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PageResult<ProductResponseDTO>> listProducts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) Boolean kitComponentEligible,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "ID") ProductSortField sort,
            @RequestParam(defaultValue = "ASC") SortDirection direction) {
        ProductFilter filter = new ProductFilter(search, category, brand, active, type, kitComponentEligible, status);
        PageResult<Product> result = estoqueUseCase.listProducts(page, size, filter, sort, direction);
        PageResult<ProductResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Resumo pré-calculado do catálogo de estoque",
            description = "Números agregados (contagem de produtos/variantes, valor em estoque a "
                    + "custo, alertas de reposição por severidade, categoria com mais produtos) "
                    + "para telas/widgets que só precisam do resumo, sem baixar o catálogo "
                    + "inteiro — badge de alertas, KPIs do Catálogo, painel de Estoque do Dashboard."
                    + "\n\n**Regra de `valorEstoqueCusto`** (EST-C022, antes só implícita, o que "
                    + "fazia servidor e tela mostrarem números diferentes): soma "
                    + "`quantidade × custo` de **todos os depósitos**, com o custo resolvido em "
                    + "cascata — `averageCost` do saldo (custo médio ponderado das entradas reais), "
                    + "senão o `costPrice` da variação, o do produto pai da variação, o do produto; "
                    + "saldo sem custo conhecido contribui **zero**.\n\n"
                    + "Produto em `RASCUNHO` fica **fora**: não é mercadoria da loja, é cadastro em "
                    + "construção. Produto e depósito **inativos ficam dentro** — desativar tira de "
                    + "circulação, não da prateleira, e o total precisa bater com a contagem "
                    + "física.\n\n**`alertasCriticos`/`alertasAtencao`** contam pontos de "
                    + "reposição cadastrados, tratando SKU **sem linha de saldo como quantidade "
                    + "zero** (EST-C019) — é o caso mais grave e era justamente o que sumia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ') and hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<EstoqueSummaryResponseDTO> getSummary() {
        return ResponseEntity.ok(converter.toResponse(estoqueUseCase.getSummary()));
    }

    @Operation(summary = "Busca um produto por SKU",
            description = "Aceita o SKU do produto pai ou o de qualquer variação — nos dois casos "
                    + "devolve o produto pai, que é onde moram categoria e precificação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<ProductResponseDTO> getProduct(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku) {
        return ResponseEntity.ok(converter.toResponse(estoqueUseCase.findProductBySku(sku)));
    }

    @Operation(summary = "Resolve um produto pelo código de barras",
            description = "Aceita o código de barras do produto pai ou o de qualquer variação — "
                    + "nos dois casos devolve o produto pai. Caminho de leitura do scanner do PDV.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Código de barras não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/by-barcode/{barcode}")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<ProductResponseDTO> getProductByBarcode(
            @PathVariable @NotBlank @Size(min = 8, max = 14) String barcode) {
        return ResponseEntity.ok(converter.toResponse(estoqueUseCase.findProductByBarcode(barcode)));
    }

    // ── Categorias do catálogo ───────────────────────────────────────────────

    @Operation(summary = "Lista categorias do catálogo, incluindo as inativas",
            description = "Ordenadas por destaque, depois ordem de exibição, depois nome — a mesma "
                    + "ordem que a vitrine usa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PageResult<CategoryResponseDTO>> listCategories(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<Category> result = estoqueUseCase.listCategories(page, size);
        List<Long> categoryIds = result.content().stream().map(Category::id).toList();
        Map<Long, Long> productCounts = estoqueUseCase.countProductsByCategoryIds(categoryIds);
        Map<Long, BigDecimal> averageMargins = estoqueUseCase.averageMarginPercentByCategoryIds(categoryIds);
        return ResponseEntity.ok(new PageResult<>(
                result.content().stream()
                        .map(c -> categoryConverter.toResponse(c, productCounts.getOrDefault(c.id(), 0L),
                                averageMargins.get(c.id())))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Remove uma categoria do catálogo",
            description = "Bloqueado com 409 se houver produto vinculado — a FK denormalizada não "
                    + "tem ON DELETE, então apagar deixaria o vínculo órfão. Para remover, primeiro "
                    + "reatribua os produtos a outra categoria.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Categoria com produto vinculado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_CATEGORY_MANAGE')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        estoqueUseCase.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lista marcas do catálogo, incluindo as inativas",
            description = "`search` filtra por trecho no nome, sem diferenciar maiúsculas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/brands")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PageResult<BrandResponseDTO>> listBrands(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String search) {
        PageResult<Brand> result = estoqueUseCase.listBrands(search, page, size);
        List<Long> brandIds = result.content().stream().map(Brand::id).toList();
        Map<Long, Long> productCounts = estoqueUseCase.countProductsByBrandIds(brandIds);
        Map<Long, BigDecimal> averageMargins = estoqueUseCase.averageMarginPercentByBrandIds(brandIds);
        return ResponseEntity.ok(new PageResult<>(
                result.content().stream()
                        .map(b -> brandConverter.toResponse(b, productCounts.getOrDefault(b.id(), 0L),
                                averageMargins.get(b.id())))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Remove uma marca do catálogo",
            description = "Bloqueado com 409 se houver produto vinculado — a FK denormalizada não "
                    + "tem ON DELETE, então apagar deixaria o vínculo órfão. Para remover, primeiro "
                    + "reatribua os produtos a outra marca.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Marca não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Marca com produto vinculado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_BRAND_MANAGE')")
    public ResponseEntity<Void> deleteBrand(@PathVariable Long id) {
        estoqueUseCase.deleteBrand(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cria uma marca do catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada"),
            @ApiResponse(responseCode = "409", description = "Já existe marca com esse nome", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/brands")
    @PreAuthorize("hasAuthority('ESTOQUE_BRAND_MANAGE')")
    public ResponseEntity<BrandResponseDTO> createBrand(@Valid @RequestBody BrandRequest request,
            Authentication authentication) {
        Brand created = estoqueUseCase.createBrand(request.getName());
        publisher.publishEvent(AuditEvent.of(EventType.BRAND_CREATED,
                authentication.getName(), Map.of("brandId", String.valueOf(created.id()),
                        "name", created.name())));
        return ResponseEntity.created(URI.create("/estoque/brands/" + created.id()))
                .body(brandConverter.toResponse(created));
    }

    @Operation(summary = "Renomeia uma marca",
            description = "Propaga o novo nome para todos os produtos vinculados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Marca não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já existe marca com esse nome", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_BRAND_MANAGE')")
    public ResponseEntity<BrandResponseDTO> updateBrand(@PathVariable Long id,
            @Valid @RequestBody BrandPatchRequest request, Authentication authentication) {
        Brand updated = estoqueUseCase.updateBrand(id, request.getName());
        publisher.publishEvent(AuditEvent.of(EventType.BRAND_UPDATED,
                authentication.getName(), Map.of("brandId", String.valueOf(id))));
        return ResponseEntity.ok(brandConverter.toResponse(updated));
    }

    @Operation(summary = "Ativa ou desativa uma marca",
            description = "Marca inativa some da listagem ativa, mas os produtos vinculados continuam "
                    + "à venda — mesma régua de categoria.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Marca não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/brands/{id}/active")
    @PreAuthorize("hasAuthority('ESTOQUE_BRAND_MANAGE')")
    public ResponseEntity<BrandResponseDTO> setBrandActive(@PathVariable Long id,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        Brand updated = estoqueUseCase.setBrandActive(id, request.getActive());
        publisher.publishEvent(AuditEvent.of(
                Boolean.TRUE.equals(request.getActive()) ? EventType.BRAND_ACTIVATED : EventType.BRAND_DEACTIVATED,
                authentication.getName(), Map.of("brandId", String.valueOf(id))));
        return ResponseEntity.ok(brandConverter.toResponse(updated));
    }

    // ── Vocabulário de atributos ─────────────────────────────────────────────

    @Operation(summary = "Lista os tipos de atributo cadastrados",
            description = "Vocabulário de nomes usado por `ProductAttribute.type` no cadastro de "
                    + "produto/variação — sugestão/consistência, não restrição: um atributo pode "
                    + "usar um tipo não cadastrado aqui.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/attribute-types")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<List<String>> listAttributeTypes() {
        return ResponseEntity.ok(estoqueUseCase.listAttributeTypes().stream().map(AttributeType::name).toList());
    }

    @Operation(summary = "Cadastra um novo tipo de atributo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado"),
            @ApiResponse(responseCode = "409", description = "Já existe tipo de atributo com esse nome", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/attribute-types")
    @PreAuthorize("hasAuthority('ESTOQUE_ATTRIBUTE_MANAGE')")
    public ResponseEntity<Void> createAttributeType(@Valid @RequestBody AttributeTypeRequest request,
            Authentication authentication) {
        AttributeType created = estoqueUseCase.createAttributeType(request.getName());
        publisher.publishEvent(AuditEvent.of(EventType.ATTRIBUTE_TYPE_CREATED,
                authentication.getName(), Map.of("attributeTypeId", String.valueOf(created.id()),
                        "name", created.name())));
        return ResponseEntity.created(URI.create("/estoque/attribute-types/" + created.id())).build();
    }

    @Operation(summary = "Cria uma categoria do catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada"),
            @ApiResponse(responseCode = "409", description = "Já existe categoria com esse nome", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('ESTOQUE_CATEGORY_MANAGE')")
    public ResponseEntity<CategoryResponseDTO> createCategory(@Valid @RequestBody CategoryRequest request,
            Authentication authentication) {
        Category created = estoqueUseCase.createCategory(request.getName(), request.isFeatured(),
                request.getDisplayOrder());
        publisher.publishEvent(AuditEvent.of(EventType.CATEGORY_CREATED,
                authentication.getName(), Map.of("categoryId", String.valueOf(created.id()),
                        "name", created.name())));
        return ResponseEntity.created(URI.create("/estoque/categories/" + created.id()))
                .body(categoryConverter.toResponse(created));
    }

    @Operation(summary = "Altera parcialmente uma categoria (nome, destaque e/ou ordem)",
            description = "Campo ausente ou nulo é mantido. Renomear propaga o novo nome para "
                    + "todos os produtos vinculados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já existe categoria com esse nome", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_CATEGORY_MANAGE')")
    public ResponseEntity<CategoryResponseDTO> updateCategory(@PathVariable Long id,
            @Valid @RequestBody CategoryPatchRequest request, Authentication authentication) {
        Category updated = estoqueUseCase.updateCategory(id, request.getName(), request.getFeatured(),
                request.getDisplayOrder());
        publisher.publishEvent(AuditEvent.of(EventType.CATEGORY_UPDATED,
                authentication.getName(), Map.of("categoryId", String.valueOf(id))));
        return ResponseEntity.ok(categoryConverter.toResponse(updated));
    }

    @Operation(summary = "Ativa ou desativa uma categoria",
            description = "Categoria inativa some da vitrine, mas os produtos vinculados continuam "
                    + "à venda — categoria é organização de vitrine, não permissão de venda.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/categories/{id}/active")
    @PreAuthorize("hasAuthority('ESTOQUE_CATEGORY_MANAGE')")
    public ResponseEntity<CategoryResponseDTO> setCategoryActive(@PathVariable Long id,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        Category updated = estoqueUseCase.setCategoryActive(id, request.getActive());
        publisher.publishEvent(AuditEvent.of(
                Boolean.TRUE.equals(request.getActive()) ? EventType.CATEGORY_ACTIVATED : EventType.CATEGORY_DEACTIVATED,
                authentication.getName(), Map.of("categoryId", String.valueOf(id))));
        return ResponseEntity.ok(categoryConverter.toResponse(updated));
    }

    @Operation(summary = "Faz upload de uma imagem de produto e devolve a URL pública",
            description = "Recebe `multipart/form-data` com o campo `file` (JPEG, PNG ou WebP). "
                    + "Não recebe SKU: a imagem é enviada enquanto o produto ainda está sendo "
                    + "preenchido, e a URL devolvida é usada depois em `imageUrl`/`images` do "
                    + "cadastro. A URL é pública e servida por `GET /product-images/{filename}`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Arquivo ausente, formato não aceito ou acima do limite", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping(value = "/products/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<Map<String, String>> uploadProductImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (file == null || file.isEmpty()) throw new InvalidImageFormatException();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            // Falha ao ler o corpo do multipart é requisição malformada, não erro do servidor —
            // mesmo tratamento que AvatarController dá ao caso.
            throw new InvalidImageFormatException();
        }
        String imageUrl = productImageUseCase.upload(bytes);
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_IMAGE_UPLOADED,
                authentication.getName(), Map.of("imageUrl", imageUrl)));
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    @Operation(summary = "Cria um produto (SKU pai) com suas variações e, opcionalmente, sua precificação",
            description = "O bloco `pricing` é opcional — omitido, o produto nasce sem preço. "
                    + "Enviá-lo exige `ESTOQUE_PRODUCT_PRICE_MANAGE` além de `ESTOQUE_PRODUCT_MANAGE`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "SKU já cadastrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/products")
    // touchesPricing() e não "#request.pricing == null": desde EST-F020 o preço também pode vir
    // dentro de variants[], e checar só a raiz deixaria quem tem apenas ESTOQUE_PRODUCT_MANAGE
    // precificar pela porta lateral. Mesmo raciocínio para touchesStock()/ESTOQUE_STOCK_MANAGE
    // (EST-F023): sem isso, criar produto com initialStock seria uma porta lateral para lançar
    // movimentação de estoque sem a permissão que POST /estoque/movements já exige.
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE') "
            + "and (!#request.touchesPricing() or hasAuthority('ESTOQUE_PRODUCT_PRICE_MANAGE')) "
            + "and (!#request.touchesStock() or hasAuthority('ESTOQUE_STOCK_MANAGE'))")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequest request,
            Authentication authentication) {
        List<ProductVariant> variants = converter.toVariants(request.getVariants());
        InitialStockRequest initialStock = request.getInitialStock();
        Product created = estoqueUseCase.createProduct(request.getSku(), request.getName(), request.getCategory(),
                variants, converter.toPricing(request.getPricing()), request.getBrand(), request.getImageUrl(),
                request.isOnSale(), request.isSuperPromo(), request.getDescription(), request.getVideoUrl(),
                request.getImages(), converter.toAttributes(request.getAttributes()), request.getCategoryId(),
                request.getBarcode(), request.getUnit(), request.isSampleProduct(), request.isKitComponentEligible(),
                request.getVisibleInPos(), request.getVisibleInMarketplace(), request.getType(),
                initialStock == null ? null : new EstoqueUseCase.InitialStockCommand(initialStock.getWarehouseCode(),
                        initialStock.getQuantity(), initialStock.getLotCode(), initialStock.getExpiryDate()),
                authentication.getName(), converter.toKitComponentCommands(request.getComponents()),
                request.getStatus(), request.getBrandId(),
                new EstoqueUseCase.TableSessionCommand(request.getAvailableForTable(),
                        request.getSessionProduct(), request.getSessionsPerUnit(), request.getOpenRoshPrice()));
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_CREATED,
                authentication.getName(), Map.of("sku", created.sku())));
        return ResponseEntity.created(URI.create("/estoque/products/" + created.sku()))
                .body(converter.toResponse(created));
    }

    @Operation(summary = "Altera parcialmente um produto (nome, categoria e/ou precificação)",
            description = "Campo ausente ou nulo é mantido, inclusive dentro de `pricing`. "
                    + "Não altera o SKU nem as variações. Mandar o bloco `pricing` exige "
                    + "`ESTOQUE_PRODUCT_PRICE_MANAGE` além de `ESTOQUE_PRODUCT_MANAGE`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}")
    // touchesPricing() e não "#request.pricing == null": desde EST-F020 o preço também pode vir
    // dentro de variants[], e checar só a raiz deixaria quem tem apenas ESTOQUE_PRODUCT_MANAGE
    // precificar pela porta lateral.
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE') "
            + "and (!#request.touchesPricing() or hasAuthority('ESTOQUE_PRODUCT_PRICE_MANAGE'))")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ProductPatchRequest request, Authentication authentication) {
        // Atributos ausentes precisam chegar como null ("não mexer"), não como lista vazia
        // ("apagar todos") — por isso a conversão só acontece quando o campo veio no corpo.
        Product updated = estoqueUseCase.updateProduct(sku, request.getName(), request.getCategory(),
                converter.toPricing(request.getPricing()), request.getBrand(), request.getImageUrl(),
                request.getOnSale(), request.getSuperPromo(), request.getDescription(), request.getVideoUrl(),
                request.getImages(),
                request.getAttributes() == null ? null : converter.toAttributes(request.getAttributes()),
                request.getCategoryId(), request.getBarcode(), request.getUnit(), request.getSampleProduct(),
                request.getKitComponentEligible(), request.getVisibleInPos(), request.getVisibleInMarketplace(),
                request.getStatus(), request.getBrandId(),
                new EstoqueUseCase.TableSessionCommand(request.getAvailableForTable(),
                        request.getSessionProduct(), request.getSessionsPerUnit(), request.getOpenRoshPrice()));
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_UPDATED,
                authentication.getName(), Map.of("sku", updated.sku())));
        // Evento próprio para mudança de preço: quem baixou o preço de quê e quando é a pergunta
        // que se faz depois de um fechamento de caixa estranho, e ela não pode se perder no meio
        // dos PRODUCT_UPDATED de renomeação de produto.
        if (request.getPricing() != null) {
            publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_PRICE_CHANGED,
                    authentication.getName(), Map.of(
                            "sku", updated.sku(),
                            "effectivePrice", String.valueOf(updated.pricing().effectivePrice()))));
        }
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Acrescenta uma ou mais variações novas à grade de um produto já existente",
            description = "Puramente aditivo — nenhuma variação já cadastrada é alterada ou "
                    + "removida. Não existe caminho de substituição em massa da grade; para "
                    + "excluir uma variação isolada veja "
                    + "`DELETE /estoque/products/{sku}/variants/{variantSku}`. Mandar `pricing` em "
                    + "alguma variação nova exige `ESTOQUE_PRODUCT_PRICE_MANAGE` além de "
                    + "`ESTOQUE_PRODUCT_MANAGE`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "SKU ou código de barras já cadastrado, ou produto é KIT", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/products/{sku}/variants")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE') "
            + "and (!#request.touchesPricing() or hasAuthority('ESTOQUE_PRODUCT_PRICE_MANAGE'))")
    public ResponseEntity<ProductResponseDTO> addVariants(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody AddVariantsRequest request, Authentication authentication) {
        List<ProductVariant> newVariants = converter.toVariants(request.getVariants());
        Product updated = estoqueUseCase.addVariants(sku, newVariants);
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_UPDATED,
                authentication.getName(), Map.of("sku", sku, "variantsAdded",
                        String.valueOf(newVariants.size()))));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Altera parcialmente uma variação já existente (não altera o SKU dela)",
            description = "Campo ausente ou nulo é mantido, inclusive dentro de `pricing`. Para "
                    + "tirar a variação de circulação sem apagar histórico, use `active: false` — "
                    + "é o caminho recomendado. Mandar `pricing` exige "
                    + "`ESTOQUE_PRODUCT_PRICE_MANAGE` além de `ESTOQUE_PRODUCT_MANAGE`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Produto ou variação não encontrados", content = @Content),
            @ApiResponse(responseCode = "409", description = "Código de barras já cadastrado em outro SKU", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}/variants/{variantSku}")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE') "
            + "and (!#request.touchesPricing() or hasAuthority('ESTOQUE_PRODUCT_PRICE_MANAGE'))")
    public ResponseEntity<ProductResponseDTO> updateVariant(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @PathVariable @NotBlank @Size(min = 3, max = 50) String variantSku,
            @Valid @RequestBody ProductVariantPatchRequest request, Authentication authentication) {
        Product updated = estoqueUseCase.updateVariant(sku, variantSku, request.getActive(),
                request.getAttributes() == null ? null : converter.toAttributes(request.getAttributes()),
                converter.toPricing(request.getPricing()), request.getBarcode());
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_UPDATED,
                authentication.getName(), Map.of("sku", sku, "variantSku", variantSku)));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Remove de fato uma variação da grade",
            description = "Bloqueado com 409 se houver saldo ou movimentação de estoque gravados "
                    + "para o SKU da variante, mesmo com saldo zerado hoje — "
                    + "`stock_balance`/`stock_movement` referenciam o SKU como texto livre, sem FK, "
                    + "e apagar deixaria esse histórico órfão. Para retirar uma variante de "
                    + "circulação preservando histórico, use "
                    + "`PATCH .../variants/{variantSku} {\"active\": false}` — é o caminho "
                    + "recomendado; este endpoint é para o caso concreto de variante criada por "
                    + "engano, sem nenhuma movimentação ainda.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Produto ou variação não encontrados", content = @Content),
            @ApiResponse(responseCode = "409", description = "Há saldo ou movimentação de estoque gravados para este SKU", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/products/{sku}/variants/{variantSku}")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> deleteVariant(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @PathVariable @NotBlank @Size(min = 3, max = 50) String variantSku, Authentication authentication) {
        Product updated = estoqueUseCase.deleteVariant(sku, variantSku);
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_UPDATED,
                authentication.getName(), Map.of("sku", sku, "variantSku", variantSku, "variantDeleted", true)));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Descarta um rascunho de produto ou kit (EST-F026)",
            description = "Exclusão de verdade, **restrita a `status: RASCUNHO`**. Existe porque o "
                    + "teto de 5 rascunhos orientava uma ação que não existia: o `409 "
                    + "DRAFT_LIMIT_REACHED` diz \"publique ou remova um rascunho\", e "
                    + "`PATCH .../active` com `active:false` **não** libera a vaga — `status` e "
                    + "`active` são eixos independentes. Cinco rascunhos abandonados desligavam o "
                    + "recurso para todo o tenant.\n\n"
                    + "Produto publicado responde **409** e continua saindo de circulação por "
                    + "`PATCH .../active`: apagar do catálogo deixaria órfão o histórico que "
                    + "referencia o SKU como texto livre, sem FK. Pelo mesmo motivo, rascunho com "
                    + "saldo ou movimentação gravados — no SKU pai ou em qualquer variação — "
                    + "também é recusado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rascunho excluído"),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Produto não é rascunho, ou o rascunho tem saldo/movimentação", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/products/{sku}")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku, Authentication authentication) {
        // O nome é lido antes de apagar: depois da exclusão a linha não existe mais, e um evento
        // de auditoria só com o SKU não diz o que foi descartado.
        String name = estoqueUseCase.findProductBySku(sku).name();
        estoqueUseCase.deleteProduct(sku);
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_DELETED,
                authentication.getName(), Map.of("sku", sku, "name", name)));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consulta a precificação vigente de um SKU",
            description = "Aceita SKU pai ou de variação — a variação herda o preço do pai. "
                    + "Devolve os valores derivados (preço sugerido, preço efetivo, margem) já "
                    + "calculados, para o PDV e a vitrine não reimplementarem o arredondamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PricingResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado no catálogo", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/price")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PricingResponseDTO> getProductPrice(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku) {
        return ResponseEntity.ok(converter.toResponse(estoqueUseCase.findPricingBySku(sku)));
    }

    @Operation(summary = "Ativa ou desativa um produto",
            description = "Produto desativado recusa entrada de estoque (manual ou por recebimento de Compras), "
                    + "mas continua aceitando saída e venda, para escoar o saldo remanescente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alterado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'active' ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}/active")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> setProductActive(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        Product updated = estoqueUseCase.setProductActive(sku, request.getActive());
        publisher.publishEvent(AuditEvent.of(
                request.getActive() ? EventType.PRODUCT_ACTIVATED : EventType.PRODUCT_DEACTIVATED,
                authentication.getName(), Map.of("sku", updated.sku())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Libera ou bloqueia a venda do produto base com variações (EST-F036)",
            description = "Com variações, o SKU base não existe na prateleira — o estoque está nelas — e por "
                    + "padrão não se vende (PDV, mesa, site: `400 PARENT_NOT_SELLABLE`) nem recebe entrada de "
                    + "estoque. Saída e ajuste continuam, para escoar o que ficou na base. Sem variações, o "
                    + "campo não tem efeito. Só aceita o SKU pai.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alterado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'parentSellable' ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU pai não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}/parent-sellable")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> setParentSellable(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ParentSellableRequest request, Authentication authentication) {
        Product updated = estoqueUseCase.setParentSellable(sku, request.getParentSellable());
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_UPDATED, authentication.getName(),
                Map.of("sku", updated.sku(), "parentSellable", updated.parentSellable())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Ativa ou desativa o rastreamento de lote e validade de um produto (EST-F008)",
            description = "Opt-in por SKU — só essência/carvão/perecível costuma precisar. A partir daqui, "
                    + "ENTRADA deste SKU em POST /estoque/movements passa a exigir lotCode e expiryDate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alterado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'lotTracked' ausente, ou SKU é KIT", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}/lot-tracked")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> setProductLotTracked(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody LotTrackedRequest request, Authentication authentication) {
        Product updated = estoqueUseCase.setProductLotTracked(sku, request.getLotTracked());
        publisher.publishEvent(AuditEvent.of(
                request.getLotTracked() ? EventType.PRODUCT_LOT_TRACKED_ENABLED : EventType.PRODUCT_LOT_TRACKED_DISABLED,
                authentication.getName(), Map.of("sku", updated.sku())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Troca o SKU de um produto ou variação (EST-F030)",
            description = "Aceita SKU pai ou de variação. A troca é propagada para todo o sistema numa "
                    + "única transação — saldo, lotes, reservas, carrinhos, comandas, pedidos, compras, "
                    + "NF-e e receitas de kit —, inclusive o histórico: depois dela o SKU antigo não "
                    + "existe mais em lugar nenhum além da auditoria (`PRODUCT_SKU_CHANGED`). Mandar o "
                    + "mesmo SKU atual é no-op.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trocado — devolve o produto pai", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'newSku' ausente ou fora de 3..50 caracteres", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "O SKU novo já pertence a outro produto ou variação", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}/sku")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> changeProductSku(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ChangeSkuRequest request, Authentication authentication) {
        String newSku = request.getNewSku().trim();
        Product updated = estoqueUseCase.changeSku(sku, newSku);
        if (!newSku.equals(sku)) {
            publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_SKU_CHANGED,
                    authentication.getName(), Map.of("oldSku", sku, "newSku", newSku, "productSku", updated.sku())));
        }
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Lista os lotes de um SKU em um depósito (EST-F008)",
            description = "Do que vence primeiro em diante. Lista vazia se o SKU não é lote-rastreado ou "
                    + "ainda não recebeu nenhum lote — não é erro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — lista vazia se não houver lote"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/lots")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<List<StockLotResponseDTO>> listStockLots(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode) {
        return ResponseEntity.ok(estoqueUseCase.listStockLots(sku, warehouseCode).stream()
                .map(lot -> converter.toResponse(lot, warehouseCode)).toList());
    }

    @Operation(summary = "Cria um depósito (loja física ou e-commerce)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado", content = @Content(schema = @Schema(implementation = WarehouseResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Código já cadastrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_MANAGE')")
    public ResponseEntity<WarehouseResponseDTO> createWarehouse(@Valid @RequestBody WarehouseRequest request,
            Authentication authentication) {
        Warehouse created = estoqueUseCase.createWarehouse(request.getCode(), request.getName(),
                warehouseConverter.toType(request.getType()));
        publisher.publishEvent(AuditEvent.of(EventType.WAREHOUSE_CREATED,
                authentication.getName(), Map.of("code", created.code())));
        return ResponseEntity.created(URI.create("/estoque/warehouses/" + created.code()))
                .body(warehouseConverter.toResponse(created));
    }

    @Operation(summary = "Altera parcialmente um depósito (nome e/ou tipo)",
            description = "Campo ausente ou nulo é mantido. Não altera o código.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado", content = @Content(schema = @Schema(implementation = WarehouseResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/warehouses/{code}")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_MANAGE')")
    public ResponseEntity<WarehouseResponseDTO> updateWarehouse(
            @PathVariable @NotBlank @Size(min = 2, max = 50) String code,
            @Valid @RequestBody WarehousePatchRequest request, Authentication authentication) {
        Warehouse updated = estoqueUseCase.updateWarehouse(code, request.getName(),
                warehouseConverter.toTypeOrNull(request.getType()));
        publisher.publishEvent(AuditEvent.of(EventType.WAREHOUSE_UPDATED,
                authentication.getName(), Map.of("code", updated.code())));
        return ResponseEntity.ok(warehouseConverter.toResponse(updated));
    }

    @Operation(summary = "Ativa ou desativa um depósito",
            description = "Depósito desativado recusa entrada de estoque, mas continua despachando saída.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alterado", content = @Content(schema = @Schema(implementation = WarehouseResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'active' ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/warehouses/{code}/active")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_MANAGE')")
    public ResponseEntity<WarehouseResponseDTO> setWarehouseActive(
            @PathVariable @NotBlank @Size(min = 2, max = 50) String code,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        Warehouse updated = estoqueUseCase.setWarehouseActive(code, request.getActive());
        publisher.publishEvent(AuditEvent.of(
                request.getActive() ? EventType.WAREHOUSE_ACTIVATED : EventType.WAREHOUSE_DEACTIVATED,
                authentication.getName(), Map.of("code", updated.code())));
        return ResponseEntity.ok(warehouseConverter.toResponse(updated));
    }

    @Operation(summary = "Lista depósitos paginados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Parâmetro de paginação inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<PageResult<WarehouseResponseDTO>> listWarehouses(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<Warehouse> result = estoqueUseCase.listWarehouses(page, size);
        PageResult<WarehouseResponseDTO> response = new PageResult<>(
                result.content().stream().map(warehouseConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consulta o saldo de estoque em um depósito",
            description = "`sku` é opcional. Informado, devolve o saldo desse produto (objeto único). "
                    + "Omitido, devolve o saldo paginado de todos os produtos do depósito (`PageResult`), "
                    + "para telas de alertas de reposição.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/stock-balance")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<?> getStockBalance(
            @RequestParam(required = false) @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (sku != null) {
            StockBalance balance = estoqueUseCase.getStockBalance(sku, warehouseCode);
            return ResponseEntity.ok(warehouseConverter.toResponse(balance, warehouseCode, resolveUnit(sku)));
        }
        PageResult<StockBalance> result = estoqueUseCase.listStockBalances(warehouseCode, page, size);
        Map<String, MeasurementUnit> unitsBySku = new HashMap<>();
        PageResult<StockBalanceResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .map(b -> warehouseConverter.toResponse(b, warehouseCode,
                                unitsBySku.computeIfAbsent(b.sku(), this::resolveUnit)))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Resolve a unidade de medida de um SKU para eco em {@code StockBalanceResponseDTO}/
     * {@code StockMovementResponseDTO}. {@code null} quando o SKU não resolve mais a um produto —
     * ele guarda-se como texto livre nas tabelas de estoque, sem FK (EST-C011).
     */
    private MeasurementUnit resolveUnit(String sku) {
        try {
            return estoqueUseCase.findProductBySku(sku).unit();
        } catch (RuntimeException notFound) {
            return null;
        }
    }

    @Operation(summary = "Registra uma movimentação manual de estoque (entrada, saída ou ajuste)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registrada", content = @Content(schema = @Schema(implementation = StockBalanceResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Saldo insuficiente ou requisição inválida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/movements")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockBalanceResponseDTO> registerMovement(@Valid @RequestBody StockMovementRequest request,
            Authentication authentication) {
        // Só usa a sobrecarga com lote/custo quando o request de fato traz algum desses campos —
        // mantém o caminho simples (a maioria dos SKUs) idêntico ao de antes do EST-F008/EST-F007.
        StockBalance updated = request.getLotCode() == null && request.getExpiryDate() == null
                && request.getUnitCost() == null
                ? estoqueUseCase.adjustStock(request.getSku(), request.getWarehouseCode(),
                        movementConverter.toType(request.getType()), request.getQuantity(), request.getReason(),
                        authentication.getName())
                : estoqueUseCase.adjustStock(request.getSku(), request.getWarehouseCode(),
                        movementConverter.toType(request.getType()), request.getQuantity(), request.getReason(),
                        authentication.getName(), request.getLotCode(), request.getExpiryDate(),
                        request.getUnitCost());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("sku", request.getSku(), "warehouseCode", request.getWarehouseCode(),
                        "type", request.getType(), "quantity", request.getQuantity())));
        return ResponseEntity.created(URI.create("/estoque/stock-balance?sku=" + request.getSku()
                        + "&warehouseCode=" + request.getWarehouseCode()))
                .body(warehouseConverter.toResponse(updated, request.getWarehouseCode(), resolveUnit(request.getSku())));
    }

    @Operation(summary = "Curva ABC e giro do consumo de um período (EST-F011)",
            description = "Classifica os SKUs por **valor consumido** (quantidade que saiu × custo "
                    + "médio vigente) na regra de Pareto: A até 80% do acumulado, B até 95%, C o "
                    + "resto. É o relatório de priorização de compra — responde onde o dinheiro "
                    + "está, não o que sai mais vezes: a essência cara que sai pouco pesa mais no "
                    + "caixa do que o carvão barato que sai sempre. A fonte é o ledger de `SAIDA` e "
                    + "não as vendas, porque cortesia, perda e conversão saem da prateleira sem "
                    + "virar venda e também precisam ser repostas. `turnover` é o consumo dividido "
                    + "pelo saldo atual, e vem **nulo** quando o saldo é zero — um SKU que acabou "
                    + "tem giro desconhecido, não infinito. `warehouseCode` omitido agrega a loja "
                    + "inteira.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/analytics/abc")
    @PreAuthorize("hasAnyAuthority('ESTOQUE_PRODUCT_READ','ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<List<AbcEntryResponseDTO>> findAbcAnalysis(
            @RequestParam(required = false) @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        List<AbcEntryResponseDTO> response = estoqueUseCase.findAbcAnalysis(warehouseCode, from, to).stream()
                .map(e -> {
                    AbcEntryResponseDTO dto = new AbcEntryResponseDTO();
                    dto.setSku(e.sku());
                    dto.setProductName(e.productName());
                    dto.setConsumedQuantity(e.consumedQuantity());
                    dto.setConsumedValue(e.consumedValue());
                    dto.setCumulativePercent(e.cumulativePercent());
                    dto.setAbcClass(e.abcClass());
                    dto.setTurnover(e.turnover());
                    return dto;
                })
                .toList();
        return ResponseEntity.ok(response);
    }

    // ── Lata aberta (EST-F027) ───────────────────────────────────────────────

    @Operation(summary = "Latas abertas de um depósito (EST-F027)",
            description = "Os pacotes de essência já abertos no balcão, com o contador de sessões "
                    + "de cada um. É o \"3 de 5\" da tela de sessão e da aba Essências.\n\n"
                    + "A lata sai do saldo quando é **aberta**, não a cada sessão: "
                    + "`stock-balance` passa a significar *latas lacradas na prateleira*, que é o "
                    + "que o operador conta no balanço, e o consumo de dentro da lata vive aqui.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/open-packages")
    @PreAuthorize("hasAnyAuthority('ESTOQUE_PRODUCT_READ','PDV_COMANDA_MANAGE')")
    public ResponseEntity<List<OpenPackageResponseDTO>> listOpenPackages(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode) {
        return ResponseEntity.ok(estoqueUseCase.listOpenPackages(warehouseCode).stream()
                .map(open -> toResponse(open, warehouseCode))
                .toList());
    }

    @Operation(summary = "A lata aberta de um SKU (EST-F027)",
            description = "404 quando não há nenhuma aberta — estado normal, não erro: a próxima "
                    + "sessão abre uma. Distinto do 400 de produto que não é vendido por sessão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Não há lata aberta deste SKU, ou o depósito não existe", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/open-packages/{sku}")
    @PreAuthorize("hasAnyAuthority('ESTOQUE_PRODUCT_READ','PDV_COMANDA_MANAGE')")
    public ResponseEntity<OpenPackageResponseDTO> findOpenPackage(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode) {
        return ResponseEntity.ok(toResponse(estoqueUseCase.findOpenPackage(sku, warehouseCode), warehouseCode));
    }

    @Operation(summary = "Repor essência: descarta a lata em uso e abre outra (EST-F027)",
            description = "Baixa **uma** unidade do saldo e zera o contador. Existe porque a lata "
                    + "acaba antes do previsto, que é o caso comum.\n\n"
                    + "A sobra da lata descartada (`uses` menor que `sessionsPerUnit`) fica no "
                    + "histórico e **não** vira ajuste de estoque: a unidade já saiu do saldo "
                    + "quando foi aberta, e transformar o resto em perda criaria movimento para "
                    + "medir uma quantidade que ninguém mediu.\n\n"
                    + "Sem saldo para abrir a nova, responde `400 INSUFFICIENT_STOCK` e a lata "
                    + "antiga **continua aberta** — o atendente segue com o que tem na mão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reposta — devolve a lata nova"),
            @ApiResponse(responseCode = "400", description = "Sem saldo para abrir, ou SKU não é vendido por sessão", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU ou depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/open-packages/{sku}/replace")
    @PreAuthorize("hasAnyAuthority('ESTOQUE_STOCK_MANAGE','PDV_COMANDA_MANAGE')")
    public ResponseEntity<OpenPackageResponseDTO> replaceOpenPackage(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ReplaceOpenPackageRequest request, Authentication authentication) {
        OpenPackage replaced = estoqueUseCase.replaceOpenPackage(sku, request.getWarehouseCode(),
                authentication.getName());
        // A trilha do movimento de estoque já está em stock_movement; o que este evento guarda é a
        // DECISÃO de descartar a lata, que não aparece em lugar nenhum lá.
        publisher.publishEvent(AuditEvent.of(EventType.OPEN_PACKAGE_REPLACED, authentication.getName(),
                Map.of("sku", sku, "warehouseCode", request.getWarehouseCode())));
        return ResponseEntity.ok(toResponse(replaced, request.getWarehouseCode()));
    }

    // ── Embalagem (EST-F032) ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Liga um SKU à embalagem que o contém (EST-F032)",
            description = "`parentSku` contém `unitsPerParent` deste SKU — o maço contém 20 cigarros, a "
                    + "carteira contém 10 maços. A partir daí toda SAIDA deste SKU que não couber no "
                    + "disponível **abre o pai sozinha**, em cascata (unidade abre maço, maço abre carteira), "
                    + "com os dois movimentos no ledger como \"Quebra automática de embalagem\". "
                    + "Redefinir substitui a ligação.\n\n"
                    + "Ligue as **variações** (cor × embalagem), não o produto base. Recusa kit, produto base "
                    + "com variações, produto com lote, ciclo e cadeia acima de 4 níveis (`400 INVALID_PACKAGING`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ligação gravada"),
            @ApiResponse(responseCode = "400", description = "Ligação inválida (`INVALID_PACKAGING`) ou fator < 2", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/products/{sku}/packaging")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<PackagingResponseDTO> definePackaging(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody DefinePackagingRequest request, Authentication authentication) {
        SkuPackaging saved = estoqueUseCase.definePackaging(sku, request.getParentSku(), request.getUnitsPerParent());
        publisher.publishEvent(AuditEvent.of(EventType.PACKAGING_DEFINED, authentication.getName(),
                Map.of("childSku", saved.childSku(), "parentSku", saved.parentSku(),
                        "unitsPerParent", saved.unitsPerParent())));
        return ResponseEntity.ok(new PackagingResponseDTO(saved.childSku(), saved.parentSku(), saved.unitsPerParent()));
    }

    @Operation(summary = "Desliga um SKU da embalagem (EST-F032)",
            description = "A venda deste SKU deixa de abrir embalagem sozinha. Saldos não mudam.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Desligado"),
            @ApiResponse(responseCode = "404", description = "SKU sem ligação (`PACKAGING_NOT_FOUND`)", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/products/{sku}/packaging")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<Void> removePackaging(@PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            Authentication authentication) {
        estoqueUseCase.removePackaging(sku);
        publisher.publishEvent(AuditEvent.of(EventType.PACKAGING_REMOVED, authentication.getName(),
                Map.of("childSku", sku)));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "A cadeia de embalagem de um SKU (EST-F032)",
            description = "Da embalagem mais externa para a mais interna (carteira → maço → unidade), com "
                    + "o disponível de cada nível quando `warehouseCode` vem informado — a base da central "
                    + "de cigarros do PDV. SKU sem ligação devolve só ele.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SKU ou depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/packaging")
    @PreAuthorize("hasAnyAuthority('ESTOQUE_PRODUCT_READ','PDV_READ')")
    public ResponseEntity<List<PackagingLevelResponseDTO>> getPackagingChain(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam(required = false) @Size(min = 2, max = 50) String warehouseCode) {
        return ResponseEntity.ok(estoqueUseCase.getPackagingChain(sku, warehouseCode).stream()
                .map(l -> new PackagingLevelResponseDTO(l.sku(), l.containsSku(), l.containsUnits(), l.available()))
                .toList());
    }

    @Operation(summary = "Cadastrar uma lata que já está aberta (EST-F033)",
            description = "Inventário inicial das essências da mesa: registra a lata que já saiu da "
                    + "prateleira antes de o sistema saber dela, informando quantas sessões ela "
                    + "ainda rende (`usesRemaining`, entre 1 e o `sessionsPerUnit` do produto).\n\n"
                    + "**Não baixa estoque** — a lata já não está no saldo de lacradas, e baixar "
                    + "agora tiraria uma segunda. Daí em diante segue o ciclo normal: quando esgota, "
                    + "a próxima sessão abre outra com `SAIDA` de 1.\n\n"
                    + "Se já houver lata aberta do SKU no depósito, responde `409 "
                    + "OPEN_PACKAGE_ALREADY_OPEN`: para trocar, use `/replace`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cadastrada — devolve o contador"),
            @ApiResponse(responseCode = "400", description = "`usesRemaining` fora da lata (`OPEN_PACKAGE_INVALID_USES`), ou SKU não é vendido por sessão", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU ou depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já existe lata aberta deste SKU no depósito", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/open-packages/{sku}")
    @PreAuthorize("hasAnyAuthority('ESTOQUE_STOCK_MANAGE','PDV_COMANDA_MANAGE')")
    public ResponseEntity<OpenPackageResponseDTO> registerOpenPackage(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody RegisterOpenPackageRequest request, Authentication authentication) {
        OpenPackage registered = estoqueUseCase.registerOpenPackage(sku, request.getWarehouseCode(),
                request.getUsesRemaining(), authentication.getName());
        // Única entrada no contador que não passa por stock_movement: sem este evento, nada diria
        // quem declarou que a lata tinha N sessões.
        publisher.publishEvent(AuditEvent.of(EventType.OPEN_PACKAGE_REGISTERED, authentication.getName(),
                Map.of("sku", sku, "warehouseCode", request.getWarehouseCode(),
                        "usesRemaining", request.getUsesRemaining())));
        // A URI é a da leitura: GET /open-packages/{sku}?warehouseCode= devolve esta mesma lata.
        return ResponseEntity.created(URI.create("/estoque/open-packages/" + sku + "?warehouseCode="
                        + request.getWarehouseCode()))
                .body(toResponse(registered, request.getWarehouseCode()));
    }

    /**
     * O nome do produto sai de {@code resolveSaleInfo}, uma consulta por lata. É N+1 assumido, e
     * limitado por construção: existe no máximo uma lata aberta por SKU de essência num depósito,
     * e o salão trabalha com algumas dezenas de sabores — não é a listagem de catálogo, que por
     * isso é ID-first. Se um dia virar problema, o caminho é o mesmo de EST-C009: buscar os nomes
     * em lote pelos SKUs da página.
     */
    private OpenPackageResponseDTO toResponse(OpenPackage open, String warehouseCode) {
        OpenPackageResponseDTO dto = new OpenPackageResponseDTO();
        dto.setSku(open.sku());
        // findProductBySku, e não resolveSaleInfo: ler o nome não é venda, e a guarda de EST-F036
        // recusaria uma lata legada aberta no SKU base.
        dto.setProductName(estoqueUseCase.findProductBySku(open.sku()).name());
        dto.setWarehouseCode(warehouseCode);
        dto.setUses(open.uses());
        dto.setSessionsPerUnit(open.sessionsPerUnit());
        dto.setRemaining(open.remaining());
        dto.setExhausted(open.isExhausted());
        dto.setOpenedAt(open.openedAt());
        dto.setOpenedBy(open.openedBy());
        return dto;
    }

    @Operation(summary = "Converte saldo de um SKU em saldo de outro, atomicamente",
            description = "Uma `SAIDA` de `fromQuantity` em `fromSku` e uma `ENTRADA` de "
                    + "`toQuantity` em `toSku`, no mesmo depósito e na **mesma transação** — ex.: 1 "
                    + "lata de essência vira 5 sessões de narguilé. Ou os dois movimentos acontecem, "
                    + "ou nenhum. Substitui o par de `POST /estoque/movements` disparado em "
                    + "sequência pelo cliente, onde uma falha no segundo sumia com o saldo do "
                    + "primeiro. `toQuantity` é explícito: `sessionsPerUnit` do catálogo é sugestão "
                    + "de tela, não fonte de saldo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Convertido — devolve os dois saldos"),
            @ApiResponse(responseCode = "400", description = "SKUs iguais, saldo insuficiente na "
                    + "origem ou payload inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU ou depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflito de concorrência no saldo", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/conversions")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockConversionResponseDTO> convertStock(
            @Valid @RequestBody StockConversionRequest request, Authentication authentication) {
        EstoqueUseCase.StockConversionResult result = estoqueUseCase.convertStock(request.getFromSku(),
                request.getToSku(), request.getFromQuantity(), request.getToQuantity(),
                request.getWarehouseCode(), request.getReason(), authentication.getName());

        StockConversionResponseDTO response = new StockConversionResponseDTO();
        response.setFrom(warehouseConverter.toResponse(result.from(), request.getWarehouseCode(),
                resolveUnit(request.getFromSku())));
        response.setTo(warehouseConverter.toResponse(result.to(), request.getWarehouseCode(),
                resolveUnit(request.getToSku())));

        // Um evento para o ato, não um por movimento: a trilha item a item já vive em stock_movement,
        // e o que se perderia com dois eventos soltos é que as duas pontas foram a mesma decisão.
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_CONVERTED, authentication.getName(),
                Map.of("fromSku", request.getFromSku(), "toSku", request.getToSku(),
                        "fromQuantity", request.getFromQuantity(), "toQuantity", request.getToQuantity(),
                        "warehouseCode", request.getWarehouseCode())));

        return ResponseEntity.created(URI.create("/estoque/stock-balance?sku=" + request.getToSku()
                        + "&warehouseCode=" + request.getWarehouseCode()))
                .body(response);
    }

    @Operation(summary = "Lista o histórico paginado de movimentações de estoque",
            description = "`sku` e `warehouseCode` são opcionais. Omitidos, devolve o feed geral de "
                    + "movimentações (mais recentes primeiro); informados, filtram por esse SKU e/ou "
                    + "depósito. `type` e o intervalo `from`/`to` (data-hora ISO) também são "
                    + "opcionais e combináveis com os demais.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/movements")
    // EST-C015 — ler o ledger é leitura, e passa a bastar uma permissão de leitura. A regra antiga
    // ("ler expõe quem movimentou o quê, logo exige STOCK_MANAGE") não protegia nada: o vizinho
    // GET /estoque/products/{sku}/purchase-history devolve PageResult<StockMovement> — o MESMO
    // ledger, a mesma entidade — com ESTOQUE_PRODUCT_READ desde sempre. O que a regra fazia era
    // quebrar uma tela: ROLE_ATENDENTE tem PRODUCT_READ e não tem STOCK_MANAGE, e o interceptor do
    // admin manda todo GET 403 para /access-denied — o operador era expulso em vez de avisado.
    // O POST logo acima continua em STOCK_MANAGE: escrever saldo é outra coisa.
    @PreAuthorize("hasAnyAuthority('ESTOQUE_PRODUCT_READ','ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<StockMovementResponseDTO>> listMovements(
            @RequestParam(required = false) @Size(min = 3, max = 50) String sku,
            @RequestParam(required = false) @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<StockMovement> result = estoqueUseCase.listMovements(sku, warehouseCode, type, from, to, page,
                size);
        Map<Long, String> warehouseCodesById = warehouseCode != null ? Map.of() : resolveWarehouseCodes(result);
        Map<String, MeasurementUnit> unitsBySku = new HashMap<>();
        PageResult<StockMovementResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .map(m -> movementConverter.toResponse(m,
                                warehouseCode != null ? warehouseCode : warehouseCodesById.get(m.warehouseId()),
                                unitsBySku.computeIfAbsent(m.sku(), this::resolveUnit)))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Resolve, num lote só, o {@code code} dos depósitos distintos que aparecem numa página de
     * movimentações sem {@code warehouseCode} informado — a entidade só guarda {@code warehouseId}.
     */
    private Map<Long, String> resolveWarehouseCodes(PageResult<StockMovement> result) {
        Map<Long, String> codesById = new HashMap<>();
        for (StockMovement movement : result.content()) {
            codesById.computeIfAbsent(movement.warehouseId(),
                    id -> estoqueUseCase.getWarehouse(id).code());
        }
        return codesById;
    }

    @Operation(summary = "Histórico de compras (últimas entradas) de um SKU num depósito",
            description = "Mais recentes primeiro. Substitui a varredura de "
                    + "`GET /estoque/movements?sku=...&size=20` procurando a primeira ENTRADA, que "
                    + "perde a referência em SKUs de giro rápido com mais de 20 movimentações desde "
                    + "a última entrada. `supplierId`/`supplierName`/`goodsReceiptId` ficam nulos em "
                    + "entradas manuais e em toda entrada anterior à introdução do vínculo com "
                    + "recebimento — limitação conhecida de dado histórico, não erro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/purchase-history")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PageResult<PurchaseHistoryResponseDTO>> listPurchaseHistory(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<StockMovement> result = estoqueUseCase.listPurchaseHistory(sku, warehouseCode, page, size);
        Map<Long, Supplier> suppliersByReceiptId = new HashMap<>();
        PageResult<PurchaseHistoryResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .map(m -> {
                            Supplier supplier = m.goodsReceiptId() == null
                                    ? null
                                    : suppliersByReceiptId.computeIfAbsent(m.goodsReceiptId(), this::resolveSupplier);
                            return movementConverter.toPurchaseHistoryResponse(m,
                                    supplier == null ? null : supplier.id(),
                                    supplier == null ? null : supplier.legalName());
                        })
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /** {@code null} quando o recebimento ou o fornecedor não resolvem mais (dado histórico). */
    private Supplier resolveSupplier(Long goodsReceiptId) {
        return comprasUseCase.findGoodsReceiptById(goodsReceiptId)
                .flatMap(receipt -> comprasUseCase.findSupplierById(receipt.supplierId()))
                .orElse(null);
    }

    // ── Lista de Reposição ───────────────────────────────────────────────────

    @Operation(summary = "Lista os itens anotados na lista de reposição de um depósito",
            description = "Mais recentemente anotados primeiro. Os campos de estoque são um "
                    + "snapshot tirado no momento do POST — nunca recalculados aqui.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/replenishment-list")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<List<ReplenishmentListItemResponseDTO>> listReplenishmentItems(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode) {
        List<ReplenishmentListItem> items = estoqueUseCase.listReplenishmentItems(warehouseCode);
        return ResponseEntity.ok(items.stream().map(replenishmentListConverter::toResponse).toList());
    }

    @Operation(summary = "Anota (ou reanota) um item na lista de reposição",
            description = "Se o SKU já estiver na lista deste depósito, substitui o item — não "
                    + "duplica. Tira um snapshot de produto/saldo/ponto de reposição/custo/última "
                    + "compra no momento da chamada; se o saldo mudar depois, a intenção de compra "
                    + "já anotada continua valendo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Anotado"),
            @ApiResponse(responseCode = "404", description = "SKU ou depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/replenishment-list/items")
    @PreAuthorize("hasAuthority('ESTOQUE_REPLENISHMENT_MANAGE')")
    public ResponseEntity<ReplenishmentListItemResponseDTO> upsertReplenishmentItem(
            @Valid @RequestBody ReplenishmentItemRequest request, Authentication authentication) {
        ReplenishmentListItem created = estoqueUseCase.upsertReplenishmentItem(request.getSku(),
                request.getWarehouseCode(), request.getQuantity(), request.getNote(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.REPLENISHMENT_ITEM_ADDED, authentication.getName(),
                Map.of("sku", request.getSku(), "warehouseCode", request.getWarehouseCode())));
        return ResponseEntity.status(201).body(replenishmentListConverter.toResponse(created));
    }

    @Operation(summary = "Altera quantity e/ou note de um item já anotado",
            description = "Os dois únicos campos editáveis depois do POST — os demais são snapshot, "
                    + "congelados no momento da anotação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Item não encontrado para este SKU/depósito", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/replenishment-list/items/{sku}")
    @PreAuthorize("hasAuthority('ESTOQUE_REPLENISHMENT_MANAGE')")
    public ResponseEntity<ReplenishmentListItemResponseDTO> updateReplenishmentItem(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            @Valid @RequestBody ReplenishmentItemPatchRequest request, Authentication authentication) {
        ReplenishmentListItem updated = estoqueUseCase.updateReplenishmentItem(sku, warehouseCode,
                request.getQuantity(), request.getNote());
        publisher.publishEvent(AuditEvent.of(EventType.REPLENISHMENT_ITEM_UPDATED, authentication.getName(),
                Map.of("sku", sku, "warehouseCode", warehouseCode)));
        return ResponseEntity.ok(replenishmentListConverter.toResponse(updated));
    }

    @Operation(summary = "Remove um item da lista de reposição",
            description = "Idempotente: 204 mesmo quando o item já não existia.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removido (ou já não existia)"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/replenishment-list/items/{sku}")
    @PreAuthorize("hasAuthority('ESTOQUE_REPLENISHMENT_MANAGE')")
    public ResponseEntity<Void> deleteReplenishmentItem(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            Authentication authentication) {
        estoqueUseCase.deleteReplenishmentItem(sku, warehouseCode);
        publisher.publishEvent(AuditEvent.of(EventType.REPLENISHMENT_ITEM_REMOVED, authentication.getName(),
                Map.of("sku", sku, "warehouseCode", warehouseCode)));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Limpa a lista de reposição inteira de um depósito")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lista limpa"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/replenishment-list")
    @PreAuthorize("hasAuthority('ESTOQUE_REPLENISHMENT_MANAGE')")
    public ResponseEntity<Void> clearReplenishmentList(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            Authentication authentication) {
        estoqueUseCase.clearReplenishmentList(warehouseCode);
        publisher.publishEvent(AuditEvent.of(EventType.REPLENISHMENT_LIST_CLEARED, authentication.getName(),
                Map.of("warehouseCode", warehouseCode)));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Define o ponto de reposição (quantidade mínima) de um SKU em um depósito")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Definido"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/products/{sku}/reorder-point")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<Void> setReorderPoint(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ReorderPointRequest request, Authentication authentication) {
        estoqueUseCase.setReorderPoint(sku, request.getWarehouseCode(), request.getMinQuantity());
        publisher.publishEvent(AuditEvent.of(EventType.REORDER_POINT_SET, authentication.getName(),
                Map.of("sku", sku, "warehouseCode", request.getWarehouseCode(),
                        "minQuantity", request.getMinQuantity())));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove o ponto de reposição de um SKU em um depósito",
            description = "Idempotente: 204 mesmo quando não havia ponto cadastrado — não é erro "
                    + "remover o que não existe. Use para desmarcar de fato o \"estoque mínimo "
                    + "próprio\" de uma variação; um PUT que para de enviar o valor não apaga o "
                    + "mínimo antigo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removido (ou já não existia)"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/products/{sku}/reorder-point")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<Void> deleteReorderPoint(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            Authentication authentication) {
        estoqueUseCase.deleteReorderPoint(sku, warehouseCode);
        publisher.publishEvent(AuditEvent.of(EventType.REORDER_POINT_DELETED, authentication.getName(),
                Map.of("sku", sku, "warehouseCode", warehouseCode)));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consulta o ponto de reposição de um SKU em um depósito",
            description = "`minQuantity` nulo significa que não há ponto de reposição configurado "
                    + "para este SKU/depósito.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/reorder-point")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<ReorderPointResponseDTO> getReorderPoint(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode) {
        Optional<ReorderPoint> reorderPoint = estoqueUseCase.getReorderPoint(sku, warehouseCode);
        return ResponseEntity.ok(warehouseConverter.toResponse(reorderPoint.orElse(null), sku, warehouseCode));
    }

    @Operation(summary = "Lista paginada dos pontos de reposição configurados em um depósito",
            description = "Alimenta telas de alertas de reposição junto com GET /estoque/stock-balance "
                    + "(sem `sku`) — o cliente cruza os dois lotes por SKU.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/reorder-points")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<PageResult<ReorderPointResponseDTO>> listReorderPoints(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<ReorderPoint> result = estoqueUseCase.listReorderPoints(warehouseCode, page, size);
        PageResult<ReorderPointResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .map(rp -> warehouseConverter.toResponse(rp, rp.sku(), warehouseCode))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------------------------
    // Kits (EST-F015) — virtuais, de um nível só (§2.10 do plano)
    // ------------------------------------------------------------------------------------

    @Operation(summary = "Define (substitui integralmente) a receita de um kit",
            description = "PUT idempotente: promove o produto a KIT como efeito colateral. Componente "
                    + "precisa ser SIMPLES — kit dentro de kit é proibido por construção.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definida", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "SKU do kit ou de algum componente não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Receita vazia, autorreferência, componente duplicado, componente não SIMPLES, kit com variações, ou SKU já é componente de outro kit", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/products/{sku}/kit")
    @PreAuthorize("hasAuthority('ESTOQUE_KIT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> defineKitRecipe(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody KitRecipeRequest request, Authentication authentication) {
        Product updated = estoqueUseCase.defineKitRecipe(sku, converter.toKitComponentCommands(request));
        publisher.publishEvent(AuditEvent.of(EventType.KIT_RECIPE_CHANGED, authentication.getName(),
                Map.of("sku", sku, "componentCount", request.getComponents().size())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Consulta a receita vigente de um kit",
            description = "Lista vazia se o SKU existe mas nunca foi promovido a kit. Cada linha vem "
                    + "enriquecida com dados de catálogo do componente (nome, imagem, preço, se está "
                    + "ativo) — sem dado de saldo por depósito, para isso ver GET /estoque/kits/availability.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/kit")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<List<KitComponentResponseDTO>> getKitRecipe(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku) {
        return ResponseEntity.ok(estoqueUseCase.getKitRecipeDetailed(sku).stream()
                .map(converter::toKitComponentResponse).toList());
    }

    @Operation(summary = "Remove a receita de um kit e rebaixa o produto para SIMPLES",
            description = "Kit sem receita fica inutilizável em qualquer venda, então esvaziar e "
                    + "deixar de ser kit são a mesma operação aqui. Idempotente: chamar num produto "
                    + "que já é SIMPLES não faz nada e devolve 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Removida", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/products/{sku}/kit")
    @PreAuthorize("hasAuthority('ESTOQUE_KIT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> clearKitRecipe(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku, Authentication authentication) {
        Product updated = estoqueUseCase.clearKitRecipe(sku);
        publisher.publishEvent(AuditEvent.of(EventType.KIT_RECIPE_CHANGED, authentication.getName(),
                Map.of("sku", sku, "componentCount", 0)));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Disponibilidade de montagem de todos os kits de um depósito",
            description = "Resolve no servidor o que o admin hoje deriva com N+1 chamadas (catálogo "
                    + "de kits + catálogo de componentes + saldos + uma consulta de receita por "
                    + "kit). `blocked=true` filtra só os kits sem estoque de algum componente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/kits/availability")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ') and hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<PageResult<KitAvailabilityResponseDTO>> getKitsAvailability(
            @RequestParam @NotBlank String warehouseCode,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<KitAvailability> result = estoqueUseCase.getKitAvailability(warehouseCode, blocked, page, size);
        return ResponseEntity.ok(new PageResult<>(
                result.content().stream().map(converter::toKitAvailabilityResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    // ------------------------------------------------------------------------------------
    // Reserva de estoque (EST-F013/EST-F021). Só leitura aqui: quem cria, consome e libera
    // reserva hoje é o próprio módulo (checkout futuro e liquidação de pedido online no PDV),
    // não um operador via HTTP — daí não existirem POST/{id}/release neste controller.
    // ------------------------------------------------------------------------------------

    @Operation(summary = "Lista reservas de estoque paginadas, mais recentes primeiro",
            description = "`sku`, `warehouseCode` e `status` são filtros opcionais e se combinam.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito informado não existe", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/reservations")
    @PreAuthorize("hasAuthority('ESTOQUE_RESERVATION_READ')")
    public ResponseEntity<PageResult<StockReservationResponseDTO>> listReservations(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<StockReservation> result = estoqueUseCase.listReservations(sku, warehouseCode,
                reservationConverter.toStatusOrNull(status), page, size);
        Map<Long, String> warehouseCodeById = new HashMap<>();
        PageResult<StockReservationResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .map(r -> reservationConverter.toResponse(r, warehouseCodeOf(r.warehouseId(), warehouseCodeById)))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consulta uma reserva de estoque")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Reserva não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/reservations/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_RESERVATION_READ')")
    public ResponseEntity<StockReservationResponseDTO> getReservation(@PathVariable Long id) {
        StockReservation reservation = estoqueUseCase.getStockReservation(id);
        String warehouseCode = estoqueUseCase.getWarehouse(reservation.warehouseId()).code();
        return ResponseEntity.ok(reservationConverter.toResponse(reservation, warehouseCode));
    }

    /**
     * Resolve o código de um depósito com cache local ao request — a listagem de reservas pode
     * atravessar vários depósitos na mesma página, e cada um só precisa ser resolvido uma vez.
     */
    private String warehouseCodeOf(Long warehouseId, Map<Long, String> cache) {
        return cache.computeIfAbsent(warehouseId, id -> estoqueUseCase.getWarehouse(id).code());
    }

    // ------------------------------------------------------------------------------------
    // Balanço de inventário (EST-F006). O `warehouseCode` da resposta é ecoado do que chegou
    // na requisição ou relido do balanço, porque o domínio guarda só o warehouseId.
    // ------------------------------------------------------------------------------------

    @Operation(summary = "Abre um balanço de inventário para um depósito",
            description = "Só pode haver um balanço aberto por depósito.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aberto", content = @Content(schema = @Schema(implementation = StockCountResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já existe balanço aberto para o depósito", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/stock-counts")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> openStockCount(
            @Valid @RequestBody StockCountRequest request, Authentication authentication) {
        StockCount opened = estoqueUseCase.openStockCount(request.getWarehouseCode(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_COUNT_OPENED, authentication.getName(),
                Map.of("stockCountId", opened.id(), "warehouseCode", request.getWarehouseCode())));
        return ResponseEntity.created(URI.create("/estoque/stock-counts/" + opened.id()))
                .body(stockCountConverter.toResponse(opened, request.getWarehouseCode()));
    }

    @Operation(summary = "Registra a contagem física de um SKU no balanço",
            description = "Upsert por SKU: recontar sobrescreve o valor anterior. Contagem zero é válida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registrado", content = @Content(schema = @Schema(implementation = StockCountResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Balanço ou SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Balanço não está aberto", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/stock-counts/{id}/items")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> recordCountedItem(@PathVariable Long id,
            @Valid @RequestBody StockCountItemRequest request) {
        StockCount updated = estoqueUseCase.recordCountedItem(id, request.getSku(), request.getCountedQuantity(),
                request.getLotCode());
        return ResponseEntity.ok(stockCountConverter.toResponse(updated, warehouseCodeOf(updated)));
    }

    @Operation(summary = "Fecha o balanço e aplica os ajustes de saldo",
            description = "Cada SKU cuja contagem divirja do saldo do sistema gera um AJUSTE levando o saldo "
                    + "ao valor contado. Itens que bateram não geram movimentação. Tudo na mesma transação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fechado", content = @Content(schema = @Schema(implementation = StockCountResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Balanço não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Balanço não está aberto", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/stock-counts/{id}/close")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> closeStockCount(@PathVariable Long id,
            Authentication authentication) {
        StockCount closed = estoqueUseCase.closeStockCount(id, authentication.getName());
        long divergentes = closed.items().stream().filter(StockCountItem::diverges).count();
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_COUNT_CLOSED, authentication.getName(),
                Map.of("stockCountId", id, "itemCount", closed.items().size(),
                        "divergentCount", divergentes)));
        return ResponseEntity.ok(stockCountConverter.toResponse(closed, warehouseCodeOf(closed)));
    }

    @Operation(summary = "Cancela o balanço sem aplicar nenhum ajuste")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelado", content = @Content(schema = @Schema(implementation = StockCountResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Balanço não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Balanço não está aberto", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/stock-counts/{id}/cancel")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> cancelStockCount(@PathVariable Long id,
            Authentication authentication) {
        StockCount cancelled = estoqueUseCase.cancelStockCount(id);
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_COUNT_CANCELLED, authentication.getName(),
                Map.of("stockCountId", id)));
        return ResponseEntity.ok(stockCountConverter.toResponse(cancelled, warehouseCodeOf(cancelled)));
    }

    @Operation(summary = "Consulta um balanço de inventário e seus itens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Balanço não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/stock-counts/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> getStockCount(@PathVariable Long id) {
        StockCount count = estoqueUseCase.getStockCount(id);
        return ResponseEntity.ok(stockCountConverter.toResponse(count, warehouseCodeOf(count)));
    }

    @Operation(summary = "Lista os balanços de um depósito, mais recentes primeiro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/stock-counts")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<StockCountResponseDTO>> listStockCounts(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<StockCount> result = estoqueUseCase.listStockCounts(warehouseCode, page, size);
        PageResult<StockCountResponseDTO> response = new PageResult<>(
                result.content().stream().map(c -> stockCountConverter.toResponse(c, warehouseCode)).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Resolve o código do depósito de um balanço já carregado. O domínio guarda {@code warehouseId},
     * e a API fala em {@code warehouseCode} — a tradução é responsabilidade do adapter.
     */
    private String warehouseCodeOf(StockCount count) {
        return estoqueUseCase.getWarehouse(count.warehouseId()).code();
    }

    /**
     * Diagnóstico de EST-C011. Não publica {@code AuditEvent}: é leitura, e o módulo já fixa que
     * consultas não geram evento (mesma regra de {@code GET /estoque/movements}).
     */
    @Operation(summary = "Lista pares SKU/depósito com dados de estoque cujo SKU não existe no catálogo",
            description = "Diagnóstico de integridade: levanta o passivo de saldo, movimentações e pontos de "
                    + "reposição órfãos gravados antes da validação de SKU. Somente leitura — o destino de cada "
                    + "órfão (cadastrar o produto faltante ou expurgar a linha) é decisão humana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — página vazia se a base está íntegra"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/integrity/orphan-skus")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<OrphanSkuResponseDTO>> listOrphanSkus(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<OrphanSku> result = estoqueUseCase.listOrphanSkus(page, size);
        PageResult<OrphanSkuResponseDTO> response = new PageResult<>(
                result.content().stream().map(movementConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Diagnóstico de EST-C013. Mesma razão de {@link #listOrphanSkus} para não publicar
     * {@code AuditEvent}: é leitura.
     */
    @Operation(summary = "Lista pares SKU/depósito cujo reservado no saldo diverge da soma das reservas ativas",
            description = "Diagnóstico de integridade: confronta `stock_balance.reserved_quantity` com a soma das "
                    + "reservas `ACTIVE` em `stock_reservation` para o mesmo par. Divergência aqui é estoque "
                    + "travado invisível — a venda recusa por reserva e nenhuma reserva ativa a explica (ou o "
                    + "inverso). Somente leitura — a correção de cada linha é decisão humana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — página vazia se a base está íntegra"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/integrity/reservation-mismatch")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<ReservationIntegrityMismatchResponseDTO>> listReservationMismatches(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<ReservationIntegrityMismatch> result = estoqueUseCase.listReservationMismatches(page, size);
        PageResult<ReservationIntegrityMismatchResponseDTO> response = new PageResult<>(
                result.content().stream().map(reservationConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Diagnóstico de EST-F008. Mesma razão de {@link #listOrphanSkus} para não publicar
     * {@code AuditEvent}: é leitura.
     */
    @Operation(summary = "Lista pares SKU/depósito de SKU lote-rastreado cujo saldo diverge da soma dos lotes",
            description = "Diagnóstico de integridade: confronta `stock_balance.quantity` com a soma de "
                    + "`stock_lot.quantity` para o mesmo par, em SKU lote-rastreado. `stock_lot` é aditivo, sem "
                    + "FK que force a igualdade — o caminho conhecido de drift é uma SAIDA cujo FEFO não achou "
                    + "saldo suficiente nos lotes para cobrir o que o agregado já validou. Somente leitura — a "
                    + "correção de cada linha é decisão humana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — página vazia se a base está íntegra"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/integrity/lot-mismatch")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<LotIntegrityMismatchResponseDTO>> listLotMismatches(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<LotIntegrityMismatch> result = estoqueUseCase.listLotMismatches(page, size);
        PageResult<LotIntegrityMismatchResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }
}
