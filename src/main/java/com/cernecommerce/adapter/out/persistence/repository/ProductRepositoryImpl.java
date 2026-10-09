package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductAttributeEmbeddable;
import com.cernecommerce.adapter.out.persistence.entity.ProductEntity;
import com.cernecommerce.adapter.out.persistence.entity.ProductVariantEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.CategoryProductCount;
import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional
public class ProductRepositoryImpl implements ProductRepository {

    /**
     * EST-F030 — toda coluna que guarda SKU como texto, no formato {@code tabela.coluna}. Nenhuma
     * tem FK para {@code product}, então esta lista é a única coisa que garante que a troca de
     * SKU não deixa saldo, lote ou histórico órfão. Coluna nova com SKU entra aqui junto da
     * migration que a cria — o {@code ProductRepositoryPostgresIT} confere contra o
     * {@code information_schema} que nenhuma ficou de fora.
     */
    static final List<String> SKU_COLUMNS = List.of(
            "product.sku",
            "product_variant.sku",
            "product_kit_component.kit_sku",
            "product_kit_component.component_sku",
            "stock_balance.sku",
            "stock_lot.sku",
            "stock_movement.sku",
            "stock_reservation.sku",
            "stock_reorder_point.sku",
            "stock_count_item.sku",
            "open_package.sku",
            // EST-F032 — as duas pontas da embalagem (V148).
            "sku_packaging.child_sku",
            "sku_packaging.parent_sku",
            "cart_item.sku",
            "comanda_item.sku",
            // PDV-F042 — o sabor que a sessão do cardápio queimou (V147).
            "comanda_item.essence_sku",
            "order_item.sku",
            "goods_receipt_item.sku",
            "replenishment_list_item.sku",
            // CRM-F010 — o item do marcado guarda o SKU para a ficha do cliente (V137).
            "receivable_item.sku",
            "nfe_import_line.matched_sku");

    private final ProductJpaRepository productJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = new ProductEntity();
        entity.setId(product.id());
        entity.setSku(product.sku());
        entity.setName(product.name());
        entity.setCategory(product.category());
        entity.setCategoryId(product.categoryId());
        entity.setBrand(product.brand());
        entity.setBrandId(product.brandId());
        entity.setImageUrl(product.imageUrl());
        entity.setOnSale(product.onSale());
        entity.setSuperPromo(product.superPromo());
        entity.setDescription(product.description());
        entity.setVideoUrl(product.videoUrl());
        entity.setActive(product.active());
        entity.setCostPrice(product.pricing().costPrice());
        entity.setMarkupPercent(product.pricing().markupPercent());
        entity.setSalePrice(product.pricing().salePrice());
        entity.setOriginalPrice(product.pricing().originalPrice());
        entity.setType(product.type().name());
        entity.setStatus(product.status().name());
        entity.setLotTracked(product.lotTracked());
        entity.setParentSellable(product.parentSellable());
        entity.setBarcode(product.barcode());
        entity.setUnit(product.unit().name());
        entity.setSampleProduct(product.sampleProduct());
        entity.setKitComponentEligible(product.kitComponentEligible());
        entity.setVisibleInPos(product.visibleInPos());
        entity.setVisibleInMarketplace(product.visibleInMarketplace());
        entity.setAvailableForTable(product.availableForTable());
        entity.setSessionProduct(product.sessionProduct());
        entity.setSessionsPerUnit(product.sessionsPerUnit());
        entity.setOpenRoshPrice(product.openRoshPrice());
        entity.setCauseAmount(product.pricing().causeAmount());
        entity.getImages().addAll(product.images());
        entity.getAttributes().addAll(product.attributes().stream()
                .map(a -> new ProductAttributeEmbeddable(a.type(), a.value()))
                .toList());
        for (ProductVariant variant : product.variants()) {
            ProductVariantEntity variantEntity = new ProductVariantEntity();
            variantEntity.setId(variant.id());
            variantEntity.setProduct(entity);
            variantEntity.setSku(variant.sku());
            variantEntity.setActive(variant.active());
            // Preço próprio da variação (EST-F020). Nulo grava as quatro colunas nulas, que é
            // exatamente como "herda do pai" fica representado no banco.
            Pricing variantPricing = variant.pricing();
            if (variantPricing != null) {
                variantEntity.setCostPrice(variantPricing.costPrice());
                variantEntity.setMarkupPercent(variantPricing.markupPercent());
                variantEntity.setSalePrice(variantPricing.salePrice());
                variantEntity.setOriginalPrice(variantPricing.originalPrice());
                variantEntity.setCauseAmount(variantPricing.causeAmount());
            }
            variantEntity.setBarcode(variant.barcode());
            variantEntity.getAttributes().addAll(variant.attributes().stream()
                    .map(a -> new ProductAttributeEmbeddable(a.type(), a.value()))
                    .toList());
            entity.getVariants().add(variantEntity);
        }
        ProductEntity saved = productJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public int renameCategory(Long categoryId, String newName) {
        return productJpaRepository.renameCategory(categoryId, newName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllByType(ProductType type) {
        return productJpaRepository.findByType(type.name()).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countProductsByCategoryIds(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : productJpaRepository.countProductsByCategoryIdsRaw(categoryIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCategoryId(Long categoryId) {
        return productJpaRepository.countByCategoryId(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllByCategoryId(Long categoryId) {
        return productJpaRepository.findByCategoryId(categoryId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllByBrandId(Long brandId) {
        return productJpaRepository.findByBrandId(brandId).stream().map(this::toDomain).toList();
    }

    @Override
    public int renameBrand(Long brandId, String newName) {
        return productJpaRepository.renameBrand(brandId, newName);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countProductsByBrandIds(List<Long> brandIds) {
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : productJpaRepository.countProductsByBrandIdsRaw(brandIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByBrandId(Long brandId) {
        return productJpaRepository.countByBrandId(brandId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countProducts() {
        return productJpaRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countVariants() {
        return productJpaRepository.countVariants();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategoryProductCount> findCategoryWithMostProducts() {
        List<Object[]> rows = productJpaRepository.findCategoryWithMostProductsRaw(PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.get(0);
        return Optional.of(new CategoryProductCount((String) row[0], (Long) row[1]));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findBySku(String sku) {
        return productJpaRepository.findBySku(sku).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findByAnySku(String sku) {
        return productJpaRepository.findByAnySku(sku).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySku(String sku) {
        return productJpaRepository.existsBySkuOrVariantSku(sku);
    }

    @Override
    public int renameSku(String currentSku, String newSku) {
        // flush antes: alteração pendente numa entidade carregada com o SKU antigo seria gravada
        // depois do UPDATE e desfaria a troca. clear depois: as entidades da sessão ainda dizem o
        // SKU antigo, e quem reler na mesma transação precisa ver o banco.
        entityManager.flush();
        int total = 0;
        for (String column : SKU_COLUMNS) {
            String[] parts = column.split("\\.");
            // Nomes vêm da constante acima, nunca de entrada — concatenar aqui não é injeção.
            total += entityManager.createNativeQuery(
                            "UPDATE " + parts[0] + " SET " + parts[1] + " = :newSku WHERE " + parts[1] + " = :oldSku")
                    .setParameter("newSku", newSku)
                    .setParameter("oldSku", currentSku)
                    .executeUpdate();
        }
        entityManager.clear();
        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSkuActive(String sku) {
        return productJpaRepository.isSkuActive(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByBarcode(String barcode) {
        return productJpaRepository.existsByBarcodeOrVariantBarcode(barcode);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findByBarcode(String barcode) {
        return productJpaRepository.findByAnyBarcode(barcode).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> findAll(int page, int size, ProductFilter filter,
            ProductSortField sortField, SortDirection direction) {
        Pageable pageable = PageRequest.of(page, size, toSort(sortField, direction));
        Page<Long> idPage = productJpaRepository.findFilteredIds(
                likePattern(filter.search()), filter.category(), filter.brand(), filter.active(),
                filter.type() == null ? null : filter.type().name(), filter.kitComponentEligible(),
                filter.status() == null ? null : filter.status().name(), pageable);
        return toPageResult(idPage, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(ProductStatus status) {
        return productJpaRepository.countByStatus(status.name());
    }

    @Override
    @Transactional
    public void deleteBySku(String sku) {
        // Variações, atributos e componentes de kit saem por cascade/orphanRemoval do agregado,
        // como já acontece quando a coleção é reescrita em save.
        productJpaRepository.findBySku(sku).ifPresent(productJpaRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> findAllActiveAndPriced(int page, int size, Boolean onSale, Long categoryId,
            String search) {
        String normalized = search == null || search.isBlank() ? null : search.trim().toLowerCase();
        Page<Long> idPage = productJpaRepository.findActivePricedIds(
                PageRequest.of(page, size), onSale, categoryId, likePattern(normalized));
        return toPageResult(idPage, page, size);
    }

    /**
     * Segunda metade do padrão ID-first: resolve as entidades dos ids já paginados.
     *
     * <p>A ordem é reimposta a partir da página de ids em vez de vir da consulta de fetch, que tem
     * {@code ORDER BY p.id} fixo — sem isso, pedir ordenação por nome ou preço devolveria a
     * página certa de produtos na ordem errada, que é o tipo de bug que passa despercebido
     * porque o conteúdo está correto.</p>
     */
    private PageResult<Product> toPageResult(Page<Long> idPage, int page, int size) {
        List<Long> ids = idPage.getContent();
        Map<Long, ProductEntity> byId = productJpaRepository.findAllByIdsWithVariants(ids).stream()
                .collect(Collectors.toMap(ProductEntity::getId, e -> e));
        List<Product> content = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toDomain)
                .toList();
        return new PageResult<>(content, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    /**
     * Traduz o campo do domínio para o atributo JPA. O desempate por {@code id} é sempre
     * acrescentado: nome e preço não são únicos, e paginar por chave não-única faz o banco
     * devolver a mesma linha em duas páginas ou em nenhuma (EST-C012).
     */
    private static Sort toSort(ProductSortField sortField, SortDirection direction) {
        Sort.Direction jpaDirection = direction == SortDirection.DESC
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = switch (sortField) {
            case ID -> "id";
            case NAME -> "name";
            case SALE_PRICE -> "salePrice";
        };
        Sort sort = Sort.by(jpaDirection, property);
        return sortField == ProductSortField.ID ? sort : sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    /** Busca parcial em nome ou SKU. O termo já chega em minúsculas de {@link ProductFilter}. */
    private static String likePattern(String search) {
        return search == null ? null : "%" + search + "%";
    }

    private Product toDomain(ProductEntity e) {
        List<ProductVariant> variants = e.getVariants().stream()
                .map(this::toDomain)
                .toList();
        Pricing pricing = Pricing.of(e.getCostPrice(), e.getMarkupPercent(), e.getSalePrice(), e.getOriginalPrice(),
                e.getCauseAmount());
        ProductType type = ProductType.valueOf(e.getType());
        MeasurementUnit unit = MeasurementUnit.valueOf(e.getUnit());
        // Dado legado (linha anterior a EST-F023, antes do backfill da migration) lê como ATIVO.
        ProductStatus status = e.getStatus() == null ? ProductStatus.ATIVO : ProductStatus.valueOf(e.getStatus());
        List<ProductAttribute> attributes = e.getAttributes().stream()
                .map(a -> new ProductAttribute(a.getType(), a.getValue()))
                .toList();
        return Product.of(e.getId(), e.getSku(), e.getName(), e.getCategory(), e.isActive(), variants, pricing, type,
                e.isLotTracked(), e.getBrand(), e.getImageUrl(), e.isOnSale(), e.isSuperPromo(), e.getDescription(),
                e.getVideoUrl(), List.copyOf(e.getImages()), attributes, e.getCategoryId(), e.getBarcode(), unit,
                e.isSampleProduct(), e.isKitComponentEligible(), e.isVisibleInPos(), e.isVisibleInMarketplace(),
                status, e.getBrandId(), e.isAvailableForTable(), e.isSessionProduct(), e.getSessionsPerUnit(),
                e.getOpenRoshPrice(), e.isParentSellable());
    }

    private ProductVariant toDomain(ProductVariantEntity e) {
        List<ProductAttribute> attributes = e.getAttributes().stream()
                .map(a -> new ProductAttribute(a.getType(), a.getValue()))
                .toList();
        return ProductVariant.of(e.getId(), e.getSku(), attributes, e.isActive(), toVariantPricing(e), e.getBarcode());
    }

    /**
     * Reconstitui a precificação própria da variação, ou {@code null} quando as cinco colunas
     * estão vazias — é esse {@code null} que o domínio lê como "herda do pai" (EST-F020).
     */
    private static Pricing toVariantPricing(ProductVariantEntity e) {
        if (e.getCostPrice() == null && e.getMarkupPercent() == null && e.getSalePrice() == null
                && e.getOriginalPrice() == null && e.getCauseAmount() == null) {
            return null;
        }
        return Pricing.of(e.getCostPrice(), e.getMarkupPercent(), e.getSalePrice(), e.getOriginalPrice(),
                e.getCauseAmount());
    }
}
