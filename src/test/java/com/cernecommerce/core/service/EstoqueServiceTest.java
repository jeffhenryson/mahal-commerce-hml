package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.BrandHasProductsException;
import com.cernecommerce.core.domain.exception.estoque.BrandNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DraftLimitReachedException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateAttributeTypeNameException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateBrandNameException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.ReplenishmentItemNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.VariantHasStockHistoryException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.NotAPackagedSessionProductException;
import com.cernecommerce.core.domain.exception.estoque.OpenPackageNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductHasStockHistoryException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotDraftException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.SameSkuConversionException;
import com.cernecommerce.core.domain.exception.estoque.ProductVariantNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockLotNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.exception.estoque.DuplicateKitComponentException;
import com.cernecommerce.core.domain.exception.estoque.EmptyKitRecipeException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentAlreadyInUseException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentNotEligibleException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException;
import com.cernecommerce.core.domain.exception.estoque.KitCostNotEditableException;
import com.cernecommerce.core.domain.exception.estoque.KitDirectAdjustmentException;
import com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException;
import com.cernecommerce.core.domain.exception.estoque.KitInitialStockNotAllowedException;
import com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException;
import com.cernecommerce.core.domain.exception.estoque.LotExpiryDateMismatchException;
import com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedUnitCostException;
import com.cernecommerce.core.domain.model.estoque.AttributeType;
import com.cernecommerce.core.domain.model.estoque.Brand;
import com.cernecommerce.core.domain.model.estoque.CategoryProductCount;
import com.cernecommerce.core.domain.model.estoque.EstoqueSummary;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.OpenPackageCloseReason;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.exception.estoque.CategoryHasProductsException;
import com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateCategoryNameException;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderAlertCounts;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockCountStatus;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase.CatalogSaleInfo;
import com.cernecommerce.core.ports.in.EstoqueUseCase.InitialStockCommand;
import com.cernecommerce.core.ports.in.EstoqueUseCase.KitComponentCommand;
import com.cernecommerce.core.ports.in.EstoqueUseCase.TableSessionCommand;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import com.cernecommerce.core.ports.out.estoque.KitComponentRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ReorderPointRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockCountRepository;
import com.cernecommerce.core.ports.out.estoque.StockIntegrityRepository;
import com.cernecommerce.core.ports.out.estoque.StockLotRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.StockReservationRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock ProductRepository productRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockBalanceRepository stockBalanceRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock ReorderPointRepository reorderPointRepository;
    @Mock StockIntegrityRepository stockIntegrityRepository;
    @Mock StockCountRepository stockCountRepository;
    @Mock StockReservationRepository stockReservationRepository;
    @Mock NotificationUseCase notificationUseCase;
    @Mock UserRepository userRepository;
    @Mock KitComponentRepository kitComponentRepository;
    @Mock StockLotRepository stockLotRepository;
    @Mock com.cernecommerce.core.ports.out.SystemConfigPort systemConfigPort;
    @Mock CategoryRepository categoryRepository;
    @Mock com.cernecommerce.core.ports.out.estoque.BrandRepository brandRepository;
    @Mock com.cernecommerce.core.ports.out.estoque.AttributeTypeRepository attributeTypeRepository;
    @Mock com.cernecommerce.core.ports.out.estoque.ReplenishmentListRepository replenishmentListRepository;
    @Mock com.cernecommerce.core.ports.out.estoque.OpenPackageRepository openPackageRepository;
    @Mock com.cernecommerce.core.ports.out.notification.ManagerNotificationPort managerNotificationPort;

    /** Mesmo default de {@code estoque.reservation.default-ttl} em {@code CoreBeanConfig}. */
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(30);

    EstoqueService estoqueService;

    /**
     * Sem transação real nestes testes, o executor despacha na hora — é o mesmo comportamento da
     * implementação de produção quando não há sincronização de transação ativa. A agregação em
     * lote é exercitada por {@code TransactionAfterCommitExecutorTest} e pelo IT de alerta.
     */
    private final AfterCommitExecutor immediateExecutor = new AfterCommitExecutor() {
        @Override
        public <T> void accumulate(String key, T item, Consumer<List<T>> flush) {
            flush.accept(List.of(item));
        }
    };

    @BeforeEach
    void setUp() {
        estoqueService = new EstoqueService(productRepository, warehouseRepository, stockBalanceRepository,
                stockMovementRepository, reorderPointRepository, stockIntegrityRepository, stockCountRepository,
                stockReservationRepository, notificationUseCase, userRepository, immediateExecutor,
                RESERVATION_TTL, kitComponentRepository, stockLotRepository, systemConfigPort, categoryRepository,
                brandRepository, attributeTypeRepository, replenishmentListRepository, openPackageRepository,
                managerNotificationPort);
        lenient().when(reorderPointRepository.findBySkuAndWarehouseId(any(), any())).thenReturn(Optional.empty());
        // Padrão dos testes: o SKU existe no catálogo, que é a pré-condição das movimentações.
        // Os testes de createProduct e os de SKU desconhecido sobrescrevem este stub.
        lenient().when(productRepository.existsBySku(any())).thenReturn(true);
        // E está ativo, pré-condição só da ENTRADA desde EST-F018. Os testes de produto
        // desativado sobrescrevem.
        lenient().when(productRepository.isSkuActive(any())).thenReturn(true);
    }

    private List<ProductVariant> oneVariant() {
        return List.of(ProductVariant.create("NARG-M-001", List.of(new ProductAttribute("sabor", "menta"))));
    }

    @Test
    void createProduct_savesAndReturns() {
        Product saved = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant());
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenReturn(saved);

        Product result = estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant());

        assertThat(result.sku()).isEqualTo("NARG-001");
        assertThat(result.variants()).hasSize(1);
        verify(productRepository).save(any());
    }

    @Test
    void createProduct_throwsWhenSkuAlreadyExists() {
        when(productRepository.existsBySku("NARG-001")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant()))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_allowsProductWithoutVariants() {
        Product saved = Product.of(2L, "CARV-001", "Carvão Coco", "carvao", true, List.of());
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenReturn(saved);

        Product result = estoqueService.createProduct("CARV-001", "Carvão Coco", "carvao", List.of());

        assertThat(result.variants()).isEmpty();
    }

    @Test
    void createProduct_throwsWhenVariantSkuAlreadyExists() {
        // EST-C010: antes, o SKU de variação duplicado escapava até a constraint
        // uk_product_variant_sku e virava 500 em vez de 409.
        when(productRepository.existsBySku("NARG-001")).thenReturn(false);
        when(productRepository.existsBySku("NARG-M-001")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant()))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("NARG-M-001");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_throwsWhenPayloadRepeatsTheSameVariantSku() {
        List<ProductVariant> duplicated = List.of(
                ProductVariant.create("NARG-M-001", List.of(new ProductAttribute("sabor", "menta"))),
                ProductVariant.create("NARG-M-001", List.of(new ProductAttribute("sabor", "uva"))));

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", duplicated))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("NARG-M-001");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_throwsWhenVariantSkuEqualsParentSku() {
        List<ProductVariant> collidesWithParent = List.of(
                ProductVariant.create("NARG-001", List.of(new ProductAttribute("sabor", "menta"))));

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile",
                collidesWithParent))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    // ── Tipo explícito e estoque inicial na criação (EST-F023) ───────────────

    @Test
    void createProduct_tipoKitComVariantsNaoVazio_throwsKitHasVariantsException() {
        List<ProductVariant> variants = List.of(ProductVariant.create("KIT-001-A", List.of()));

        assertThatThrownBy(() -> estoqueService.createProduct("KIT-001", "Kit", "combo", variants, Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, ProductType.KIT, null, null))
                .isInstanceOf(KitHasVariantsException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_tipoKitComEstoqueInicial_throwsKitInitialStockNotAllowedException() {
        InitialStockCommand initialStock =
                new InitialStockCommand("LOJA-01", BigDecimal.TEN, null, null);

        assertThatThrownBy(() -> estoqueService.createProduct("KIT-002", "Kit", "combo", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, ProductType.KIT, initialStock, "gerente"))
                .isInstanceOf(KitInitialStockNotAllowedException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_tipoKitSemVariantsESemEstoqueInicial_nasceComoKit() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.createProduct("KIT-003", "Kit", "combo", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, ProductType.KIT, null, null);

        assertThat(result.type()).isEqualTo(ProductType.KIT);
    }

    @Test
    void createProduct_tipoAusente_nasceComoSimples() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.createProduct("SMP-001", "Produto", "categoria", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null);

        assertThat(result.type()).isEqualTo(ProductType.SIMPLES);
    }

    // ── Rascunho de produto/kit (EST-F023) ────────────────────────────────────

    @Test
    void createProduct_statusAusente_nasceAtivo() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.createProduct("EST23-001", "Produto", "categoria", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, null, null, null);

        assertThat(result.status()).isEqualTo(ProductStatus.ATIVO);
        verify(productRepository, never()).countByStatus(any());
    }

    @Test
    void createProduct_statusRascunho_persisteRascunho_semExigirCategoriaOuPreco() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.countByStatus(ProductStatus.RASCUNHO)).thenReturn(0L);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Só sku + name — nenhum outro campo obrigatório, mesma validação mínima de ATIVO.
        Product result = estoqueService.createProduct("EST23-002", "Rascunho", null, List.of(), null,
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, ProductStatus.RASCUNHO, null, null);

        assertThat(result.status()).isEqualTo(ProductStatus.RASCUNHO);
        assertThat(result.isDraft()).isTrue();
    }

    @Test
    void createProduct_rascunhoNoLimiteDeCinco_lancaDraftLimitReachedException() {
        when(productRepository.countByStatus(ProductStatus.RASCUNHO)).thenReturn(5L);

        assertThatThrownBy(() -> estoqueService.createProduct("EST23-003", "Rascunho", null, List.of(), null,
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, ProductStatus.RASCUNHO, null, null))
                .isInstanceOf(DraftLimitReachedException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_rascunhoAbaixoDoLimite_permiteCriar() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.countByStatus(ProductStatus.RASCUNHO)).thenReturn(4L);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> estoqueService.createProduct("EST23-004", "Rascunho", null, List.of(), null,
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, ProductStatus.RASCUNHO, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void updateProduct_promoveRascunhoParaAtivo_semValidacaoAdicional() {
        Product rascunho = Product.of(1L, "EST23-005", "Rascunho", null, true, List.of())
                .withStatus(ProductStatus.RASCUNHO);
        when(productRepository.findBySku("EST23-005")).thenReturn(Optional.of(rascunho));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.updateProduct("EST23-005", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, ProductStatus.ATIVO, null, null);

        assertThat(result.status()).isEqualTo(ProductStatus.ATIVO);
        verify(productRepository, never()).countByStatus(any());
    }

    @Test
    void updateProduct_transicaoParaRascunhoNoLimite_lancaDraftLimitReachedException() {
        Product ativo = Product.of(1L, "EST23-006", "Produto", "cat", true, List.of());
        when(productRepository.findBySku("EST23-006")).thenReturn(Optional.of(ativo));
        when(productRepository.countByStatus(ProductStatus.RASCUNHO)).thenReturn(5L);

        assertThatThrownBy(() -> estoqueService.updateProduct("EST23-006", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, ProductStatus.RASCUNHO, null, null))
                .isInstanceOf(DraftLimitReachedException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_editarRascunhoJaRascunho_naoContaContraOProprioLimite() {
        Product rascunho = Product.of(1L, "EST23-007", "Rascunho", null, true, List.of())
                .withStatus(ProductStatus.RASCUNHO);
        when(productRepository.findBySku("EST23-007")).thenReturn(Optional.of(rascunho));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.updateProduct("EST23-007", "Novo Nome", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, ProductStatus.RASCUNHO, null, null);

        assertThat(result.status()).isEqualTo(ProductStatus.RASCUNHO);
        // A ausência de stub para countByStatus prova que o limite não é sequer consultado quando
        // o produto já era rascunho — ver o "&&" curto-circuitado em EstoqueService.updateProduct.
        verify(productRepository, never()).countByStatus(any());
    }

    @Test
    void createProduct_comEstoqueInicial_registraEntradaNaMesmaChamada() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        // false na checagem de duplicidade (antes do save), true na checagem de requireKnownSku
        // que adjustStock faz internamente (depois do save) — o mock não tem noção de tempo, então
        // a segunda chamada precisa refletir que o produto "já existe" nesse ponto.
        when(productRepository.existsBySku(any())).thenReturn(false, true);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("NOVO-001"))
                .thenReturn(Optional.of(Product.create("NOVO-001", "Produto Novo", "testes", List.of())));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NOVO-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InitialStockCommand initialStock =
                new InitialStockCommand("LOJA-01", new BigDecimal("15"), null, null);

        Product result = estoqueService.createProduct("NOVO-001", "Produto Novo", "testes", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, initialStock, "gerente");

        assertThat(result.sku()).isEqualTo("NOVO-001");
        verify(stockMovementRepository).save(argThat(m -> m.type() == MovementType.ENTRADA
                && m.quantity().compareTo(new BigDecimal("15")) == 0
                && m.username().equals("gerente")
                && m.reason().equals("Estoque inicial no cadastro")));
        verify(stockBalanceRepository).save(argThat(b -> b.quantity().compareTo(new BigDecimal("15")) == 0));
    }

    @Test
    void createProduct_semEstoqueInicial_naoTocaEmMovimentoOuSaldo() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.createProduct("SEM-ESTOQUE-001", "Produto", "testes", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void listProducts_delegatesToRepository() {
        PageResult<Product> page = new PageResult<>(
                List.of(Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, List.of())), 0, 20, 1L, 1);
        when(productRepository.findAll(eq(0), eq(20), any(), any(), any())).thenReturn(page);

        PageResult<Product> result = estoqueService.listProducts(0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void listProducts_semArgumentos_naoFiltraEOrdenaPorIdAscendente() {
        // A sobrecarga de dois argumentos é o caminho retrocompatível: precisa continuar
        // significando "catálogo inteiro, ordem de id".
        when(productRepository.findAll(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        estoqueService.listProducts(0, 20);

        verify(productRepository).findAll(0, 20, ProductFilter.EMPTY, ProductSortField.ID, SortDirection.ASC);
    }

    @Test
    void listProducts_repassaFiltroEOrdenacaoAoRepositorio() {
        ProductFilter filter = new ProductFilter("menta", "narguile", "zomo", true);
        when(productRepository.findAll(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        estoqueService.listProducts(2, 50, filter, ProductSortField.SALE_PRICE, SortDirection.DESC);

        verify(productRepository).findAll(2, 50, filter, ProductSortField.SALE_PRICE, SortDirection.DESC);
    }

    @Test
    void listActivePricedProducts_delegatesToRepository() {
        PageResult<Product> page = new PageResult<>(
                List.of(Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, List.of())), 0, 20, 1L, 1);
        when(productRepository.findAllActiveAndPriced(0, 20, null, null, null)).thenReturn(page);

        PageResult<Product> result = estoqueService.listActivePricedProducts(0, 20, null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void changeSku_renamesEverywhereAndReturnsReloadedProduct() {
        Product renamed = Product.of(1L, "NARG-NOVO", "Narguile Aladin", "narguile", true, List.of());
        when(productRepository.existsBySku("NARG-001")).thenReturn(true);
        when(productRepository.existsBySku("NARG-NOVO")).thenReturn(false);
        when(productRepository.findByAnySku("NARG-NOVO")).thenReturn(Optional.of(renamed));

        Product result = estoqueService.changeSku("NARG-001", "  NARG-NOVO ");

        verify(productRepository).renameSku("NARG-001", "NARG-NOVO");
        assertThat(result.sku()).isEqualTo("NARG-NOVO");
    }

    @Test
    void changeSku_throwsDuplicateWhenNewSkuAlreadyExists() {
        when(productRepository.existsBySku("NARG-001")).thenReturn(true);
        when(productRepository.existsBySku("NARG-002")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.changeSku("NARG-001", "NARG-002"))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).renameSku(any(), any());
    }

    @Test
    void changeSku_throwsNotFoundWhenCurrentSkuDoesNotExist() {
        when(productRepository.existsBySku("NAO-EXISTE")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.changeSku("NAO-EXISTE", "NARG-NOVO"))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).renameSku(any(), any());
    }

    @Test
    void changeSku_sameSkuIsNoOp() {
        Product product = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, List.of());
        when(productRepository.findByAnySku("NARG-001")).thenReturn(Optional.of(product));

        estoqueService.changeSku("NARG-001", "NARG-001");

        verify(productRepository, never()).renameSku(any(), any());
    }

    @Test
    void findProductBySku_returnsProductWhenExists() {
        Product product = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant());
        when(productRepository.findByAnySku("NARG-001")).thenReturn(Optional.of(product));

        Product result = estoqueService.findProductBySku("NARG-001");

        assertThat(result.sku()).isEqualTo("NARG-001");
        assertThat(result.name()).isEqualTo("Narguile Aladin");
    }

    @Test
    void findProductBySku_throwsWhenSkuNotInCatalog() {
        when(productRepository.findByAnySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.findProductBySku("SKU-FANTASMA"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getDefaultWarehouse_resolvesConfiguredCode() {
        when(systemConfigPort.findByKey("estoque.warehouse.default-code"))
                .thenReturn(Optional.of(new com.cernecommerce.core.domain.model.config.SystemConfig(
                        "estoque.warehouse.default-code", "LOJA-01", Instant.now(), "admin")));
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));

        Warehouse result = estoqueService.getDefaultWarehouse();

        assertThat(result.code()).isEqualTo("LOJA-01");
    }

    @Test
    void getDefaultWarehouse_throwsWhenConfigKeyMissing() {
        when(systemConfigPort.findByKey("estoque.warehouse.default-code")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getDefaultWarehouse())
                .isInstanceOf(com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException.class);
    }

    @Test
    void getDefaultWarehouse_throwsWhenConfigValueBlank() {
        when(systemConfigPort.findByKey("estoque.warehouse.default-code"))
                .thenReturn(Optional.of(new com.cernecommerce.core.domain.model.config.SystemConfig(
                        "estoque.warehouse.default-code", "  ", Instant.now(), "admin")));

        assertThatThrownBy(() -> estoqueService.getDefaultWarehouse())
                .isInstanceOf(com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException.class);
    }

    @Test
    void getDefaultWarehouse_throwsWhenConfiguredWarehouseDoesNotExist() {
        when(systemConfigPort.findByKey("estoque.warehouse.default-code"))
                .thenReturn(Optional.of(new com.cernecommerce.core.domain.model.config.SystemConfig(
                        "estoque.warehouse.default-code", "GHOST", Instant.now(), "admin")));
        when(warehouseRepository.findByCode("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getDefaultWarehouse())
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void getWarehouse_returnsWarehouseWhenIdExists() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

        Warehouse result = estoqueService.getWarehouse(1L);

        assertThat(result.code()).isEqualTo("LOJA-01");
    }

    @Test
    void getWarehouse_throwsWhenIdNotFound() {
        when(warehouseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getWarehouse(99L))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void getWarehouseByCode_returnsWarehouseWhenCodeExists() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));

        Warehouse result = estoqueService.getWarehouseByCode("LOJA-01");

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void getWarehouseByCode_throwsWhenCodeNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getWarehouseByCode("INEXISTENTE"))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void createWarehouse_savesAndReturns() {
        Warehouse saved = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.empty());
        when(warehouseRepository.save(any())).thenReturn(saved);

        Warehouse result = estoqueService.createWarehouse("LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA);

        assertThat(result.code()).isEqualTo("LOJA-01");
        verify(warehouseRepository).save(any());
    }

    @Test
    void createWarehouse_throwsWhenCodeAlreadyExists() {
        when(warehouseRepository.findByCode("LOJA-01"))
                .thenReturn(Optional.of(Warehouse.of(1L, "LOJA-01", "Existente", WarehouseType.LOJA_FISICA, true)));

        assertThatThrownBy(() -> estoqueService.createWarehouse("LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA))
                .isInstanceOf(DuplicateWarehouseCodeException.class);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void listWarehouses_delegatesPagingToRepository() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findAll(1, 50)).thenReturn(new PageResult<>(List.of(warehouse), 1, 50, 1L, 1));

        PageResult<Warehouse> result = estoqueService.listWarehouses(1, 50);

        assertThat(result.content()).containsExactly(warehouse);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        verify(warehouseRepository).findAll(1, 50);
    }

    @Test
    void getStockBalance_returnsExistingBalance() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance balance = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("5.000"), 2L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(balance));

        StockBalance result = estoqueService.getStockBalance("NARG-001", "LOJA-01");

        assertThat(result.quantity()).isEqualByComparingTo("5.000");
    }

    @Test
    void getStockBalance_returnsZeroWhenNoBalanceRecordYet() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());

        StockBalance result = estoqueService.getStockBalance("NARG-001", "LOJA-01");

        assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.warehouseId()).isEqualTo(1L);
    }

    @Test
    void getStockBalance_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getStockBalance("NARG-001", "INEXISTENTE"))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void adjustStock_entrada_withoutPriorBalance_startsFromZeroAndPersists() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento inicial", "gerente");

        assertThat(result.quantity()).isEqualByComparingTo("5.000");
        verify(stockMovementRepository).save(argThat(m -> m.type() == MovementType.ENTRADA
                && m.quantity().compareTo(new BigDecimal("5.000")) == 0
                && m.username().equals("gerente")));
        verify(stockBalanceRepository).save(any());
    }

    @Test
    void adjustStock_saida_decreasesExistingBalance() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 2L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("3.000"), "Venda balcão", "gerente");

        assertThat(result.quantity()).isEqualByComparingTo("7.000");
    }

    @Test
    void adjustStock_saida_insufficientBalance_throwsAndDoesNotPersistAnything() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("2.000"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("5.000"), "Venda balcão", "gerente"))
                .isInstanceOf(InsufficientStockException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void adjustStock_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "INEXISTENTE", MovementType.ENTRADA,
                BigDecimal.ONE, "motivo", "gerente"))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    // ── Lote e validade (EST-F008) ───────────────────────────────────────────────────────────

    private Product lotTrackedProduct(String sku) {
        return Product.of(1L, sku, "Essência " + sku, "essencia", true, List.of(),
                Pricing.empty(), ProductType.SIMPLES, true);
    }

    @Test
    void adjustStock_entrada_loteRastreado_semLoteInfo_lancaMissingLotInfo() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));

        assertThatThrownBy(() -> estoqueService.adjustStock("ESS-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente", null, null))
                .isInstanceOf(MissingLotInfoException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
        verify(stockLotRepository, never()).save(any());
    }

    @Test
    void adjustStock_entrada_loteRastreado_comLoteNovo_criaLinhaDeLote() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        LocalDate expiry = LocalDate.of(2027, 3, 1);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockLotRepository.findBySkuAndWarehouseIdAndLotCode("ESS-001", 1L, "LOTE-A"))
                .thenReturn(Optional.empty());
        when(stockLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("ESS-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente", "LOTE-A", expiry);

        assertThat(result.quantity()).isEqualByComparingTo("5.000");
        verify(stockLotRepository).save(argThat(lot -> lot.lotCode().equals("LOTE-A")
                && lot.expiryDate().equals(expiry)
                && lot.quantity().compareTo(new BigDecimal("5.000")) == 0));
        verify(stockMovementRepository).save(argThat(m -> "LOTE-A".equals(m.lotCode())));
    }

    @Test
    void adjustStock_entrada_loteRastreado_comLoteExistente_somaQuantidade() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        LocalDate expiry = LocalDate.of(2027, 3, 1);
        StockLot existingLot = StockLot.of(9L, "ESS-001", 1L, "LOTE-A", expiry, new BigDecimal("3.000"), null, 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockLotRepository.findBySkuAndWarehouseIdAndLotCode("ESS-001", 1L, "LOTE-A"))
                .thenReturn(Optional.of(existingLot));
        when(stockLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.adjustStock("ESS-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("2.000"), "Recebimento", "gerente", "LOTE-A", expiry);

        verify(stockLotRepository).save(argThat(lot -> lot.id().equals(9L)
                && lot.quantity().compareTo(new BigDecimal("5.000")) == 0));
    }

    @Test
    void adjustStock_entrada_loteRastreado_validadeDivergente_lancaLotExpiryMismatch() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        LocalDate recorded = LocalDate.of(2027, 3, 1);
        LocalDate informed = LocalDate.of(2027, 4, 1);
        StockLot existingLot = StockLot.of(9L, "ESS-001", 1L, "LOTE-A", recorded, new BigDecimal("3.000"), null, 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.empty());
        when(stockLotRepository.findBySkuAndWarehouseIdAndLotCode("ESS-001", 1L, "LOTE-A"))
                .thenReturn(Optional.of(existingLot));

        assertThatThrownBy(() -> estoqueService.adjustStock("ESS-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("2.000"), "Recebimento", "gerente", "LOTE-A", informed))
                .isInstanceOf(LotExpiryDateMismatchException.class);

        verify(stockLotRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void adjustStock_entrada_naoLoteRastreado_comLoteInfo_lancaUnexpectedLotInfo() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        // findByAnySku não estubado: Optional.empty() por padrão (Mockito), lotTracked = false.

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente", "LOTE-A", LocalDate.of(2027, 3, 1)))
                .isInstanceOf(UnexpectedLotInfoException.class);

        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void adjustStock_saida_comLoteInfo_lancaUnexpectedLotInfo() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));

        assertThatThrownBy(() -> estoqueService.adjustStock("ESS-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("1.000"), "Venda", "gerente", "LOTE-A", LocalDate.of(2027, 3, 1)))
                .isInstanceOf(UnexpectedLotInfoException.class);

        verify(stockBalanceRepository, never()).save(any());
    }

    // ── Custo médio ponderado (EST-F007) ─────────────────────────────────────────────────────

    @Test
    void adjustStock_entrada_comUnitCost_atualizaAverageCost() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("5.00"), 2L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("10"), "Recebimento", "gerente", null, null, new BigDecimal("7.00"));

        assertThat(result.averageCost()).isEqualByComparingTo("6.00");
        verify(stockMovementRepository).save(argThat(m -> m.unitCost() != null
                && m.unitCost().compareTo(new BigDecimal("7.00")) == 0));
    }

    @Test
    void adjustStock_entrada_semUnitCost_naoAlteraAverageCost() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("5.00"), 2L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("10"), "Recebimento", "gerente");

        assertThat(result.averageCost()).isEqualByComparingTo("5.00");
        verify(stockMovementRepository).save(argThat(m -> m.unitCost() == null));
    }

    @Test
    void adjustStock_saida_comUnitCost_lancaUnexpectedUnitCost() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("1"), "Venda", "gerente", null, null, new BigDecimal("5.00")))
                .isInstanceOf(UnexpectedUnitCostException.class);

        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void adjustStock_ajuste_comUnitCost_lancaUnexpectedUnitCost() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.AJUSTE,
                new BigDecimal("4"), "Balanço", "gerente", null, null, new BigDecimal("5.00")))
                .isInstanceOf(UnexpectedUnitCostException.class);

        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void adjustStock_kit_comUnitCost_lancaUnexpectedUnitCost() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(
                Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));

        assertThatThrownBy(() -> estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.ENTRADA,
                BigDecimal.ONE, "motivo", "gerente", null, null, new BigDecimal("5.00")))
                .isInstanceOf(UnexpectedUnitCostException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(kitComponentRepository, never()).findByKitSku(any());
    }

    @Test
    void adjustStock_saida_loteRastreado_consomeUmLoteSoPorFefo() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "ESS-001", 1L, new BigDecimal("10.000"), 0L);
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", LocalDate.of(2027, 3, 1),
                new BigDecimal("10.000"), null, 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockLotRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(List.of(lot));
        when(stockLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.adjustStock("ESS-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("4.000"), "Venda balcão", "gerente");

        verify(stockLotRepository).save(argThat(l -> l.lotCode().equals("LOTE-A")
                && l.quantity().compareTo(new BigDecimal("6.000")) == 0));
        // SAIDA nunca carimba lotCode no ledger — pode atravessar mais de um lote.
        verify(stockMovementRepository).save(argThat(m -> m.lotCode() == null));
    }

    @Test
    void adjustStock_saida_loteRastreado_atravessaDoisLotesPorFefo() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "ESS-001", 1L, new BigDecimal("10.000"), 0L);
        // Ordem FEFO já vem pronta do repositório (a ordenação real é testada no *RepositoryIT).
        StockLot vencePrimeiro = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", LocalDate.of(2026, 8, 1),
                new BigDecimal("3.000"), null, 0L);
        StockLot venceDepois = StockLot.of(2L, "ESS-001", 1L, "LOTE-B", LocalDate.of(2027, 1, 1),
                new BigDecimal("7.000"), null, 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockLotRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(List.of(vencePrimeiro, venceDepois));
        when(stockLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.adjustStock("ESS-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("5.000"), "Venda balcão", "gerente");

        // Drena LOTE-A (3) inteiro e tira mais 2 do LOTE-B, que vence depois.
        verify(stockLotRepository).save(argThat(l -> l.lotCode().equals("LOTE-A")
                && l.quantity().signum() == 0));
        verify(stockLotRepository).save(argThat(l -> l.lotCode().equals("LOTE-B")
                && l.quantity().compareTo(new BigDecimal("5.000")) == 0));
    }

    @Test
    void adjustStock_naoLoteRastreado_naoTocaStockLotRepository() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("3.000"), "Venda balcão", "gerente");

        verifyNoInteractions(stockLotRepository);
    }

    // ── Kits (EST-F015) ──────────────────────────────────────────────────────────────────────

    private Product kitProduct(String sku, String salePrice) {
        return Product.of(1L, sku, "Kit " + sku, "combo", true, List.of(),
                Pricing.of(null, null, new BigDecimal(salePrice)), ProductType.KIT);
    }

    private Product simpleProduct(String sku, String costPrice) {
        BigDecimal cost = costPrice == null ? null : new BigDecimal(costPrice);
        return Product.of(2L, sku, "Componente " + sku, "insumo", true, List.of(),
                Pricing.of(cost, null, new BigDecimal("999.00")))
                .withKitComponentEligible(true);
    }

    @Test
    void findPricingBySku_kit_sumsComponentCosts() {
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2")),
                KitComponent.create("KIT-001", "ESS-001", BigDecimal.ONE)));
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(simpleProduct("ESS-001", "15.00")));

        Pricing derived = estoqueService.findPricingBySku("KIT-001");

        // 20,00 x 2 + 15,00 x 1 = 55,00.
        assertThat(derived.costPrice()).isEqualByComparingTo("55.00");
        assertThat(derived.salePrice()).isEqualByComparingTo("80.00");
        assertThat(derived.markupPercent()).isNull();
    }

    @Test
    void findPricingBySku_kit_costIsNullWhenAComponentHasNoCost() {
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2")),
                KitComponent.create("KIT-001", "ESS-001", BigDecimal.ONE)));
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(simpleProduct("ESS-001", null)));

        // Ausência é "desconhecido", nunca zero — mesma convenção de Pricing.
        assertThat(estoqueService.findPricingBySku("KIT-001").costPrice()).isNull();
    }

    @Test
    void getSummary_agregaOsQuatroRepositoriosNumSoResumo() {
        when(productRepository.countProducts()).thenReturn(342L);
        when(productRepository.countVariants()).thenReturn(891L);
        when(stockBalanceRepository.sumInventoryValueAtCost()).thenReturn(new BigDecimal("128450.30"));
        when(reorderPointRepository.countAlerts()).thenReturn(new ReorderAlertCounts(7, 15));
        when(productRepository.findCategoryWithMostProducts())
                .thenReturn(Optional.of(new CategoryProductCount("Bebidas", 58)));

        EstoqueSummary summary = estoqueService.getSummary();

        assertThat(summary.totalProdutos()).isEqualTo(342L);
        assertThat(summary.totalVariantes()).isEqualTo(891L);
        assertThat(summary.valorEstoqueCusto()).isEqualByComparingTo("128450.30");
        assertThat(summary.alertasCriticos()).isEqualTo(7L);
        assertThat(summary.alertasAtencao()).isEqualTo(15L);
        assertThat(summary.categoriaComMaisProdutos()).isEqualTo(new CategoryProductCount("Bebidas", 58));
    }

    @Test
    void getSummary_categoriaComMaisProdutosNuloQuandoNenhumProdutoVinculado() {
        when(productRepository.countProducts()).thenReturn(0L);
        when(productRepository.countVariants()).thenReturn(0L);
        when(stockBalanceRepository.sumInventoryValueAtCost()).thenReturn(BigDecimal.ZERO);
        when(reorderPointRepository.countAlerts()).thenReturn(ReorderAlertCounts.ZERO);
        when(productRepository.findCategoryWithMostProducts()).thenReturn(Optional.empty());

        assertThat(estoqueService.getSummary().categoriaComMaisProdutos()).isNull();
    }

    @Test
    void getStockBalance_kit_derivesMinFloorAcrossComponents() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2")),
                KitComponent.create("KIT-001", "ESS-001", new BigDecimal("3"))));
        when(stockBalanceRepository.findBySkuAndWarehouseId("CARV-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "CARV-001", 1L, new BigDecimal("10"), 0L)));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(11L, "ESS-001", 1L, new BigDecimal("7"), 0L)));

        StockBalance result = estoqueService.getStockBalance("KIT-001", "LOJA-01");

        // CARV-001: 10/2 = 5 kits possíveis; ESS-001: floor(7/3) = 2 kits possíveis; vence o menor.
        assertThat(result.quantity()).isEqualByComparingTo("2");
        assertThat(result.id()).isNull();
    }

    @Test
    void getStockBalance_kit_returnsZeroWhenRecipeEmpty() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of());

        assertThat(estoqueService.getStockBalance("KIT-001", "LOJA-01").quantity())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void adjustStock_kit_explodesIntoOneMovementPerComponent() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2"))));
        when(stockBalanceRepository.findBySkuAndWarehouseId("CARV-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "CARV-001", 1L, new BigDecimal("10"), 0L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.SAIDA, new BigDecimal("3"),
                "Venda balcão sessão #1", "gerente");

        // 2 (receita) x 3 (kits vendidos) = 6 unidades do componente.
        verify(stockMovementRepository).save(argThat(m -> m.sku().equals("CARV-001")
                && m.type() == MovementType.SAIDA
                && m.quantity().compareTo(new BigDecimal("6")) == 0
                && m.reason().equals("Venda balcão sessão #1 (kit KIT-001)")));
        // Kit nunca ganha linha própria em stock_balance.
        verify(stockMovementRepository, never()).save(argThat(m -> m.sku().equals("KIT-001")));
    }

    @Test
    void adjustStock_kit_comLoteInfo_lancaUnexpectedLotInfo() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(
                Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));

        assertThatThrownBy(() -> estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.ENTRADA,
                BigDecimal.ONE, "motivo", "gerente", "L1", java.time.LocalDate.parse("2027-01-01")))
                .isInstanceOf(UnexpectedLotInfoException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(kitComponentRepository, never()).findByKitSku(any());
    }

    @Test
    void adjustStock_kit_rejectsDirectAdjustment() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(
                Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));

        assertThatThrownBy(() -> estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.AJUSTE,
                BigDecimal.TEN, "contagem", "gerente"))
                .isInstanceOf(KitDirectAdjustmentException.class);

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void adjustStock_kit_rejectsEmptyRecipe() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(
                Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of());

        assertThatThrownBy(() -> estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.SAIDA,
                BigDecimal.ONE, "motivo", "gerente"))
                .isInstanceOf(EmptyKitRecipeException.class);
    }

    /**
     * explodeKitMovement itera a receita com um for comum, sem try/catch — o segundo componente
     * (ESS-001) estoura InsufficientStockException dentro do adjustStock recursivo e a exceção sobe
     * direto, sem ser envolvida ou agregada. Como o primeiro componente (CARV-001) já tinha sido
     * processado com sucesso antes da falha, o teste confirma tanto que ele foi persistido quanto
     * que o terceiro (CERA-001), que viria depois do que falhou, nunca chega a ser tocado —
     * ver EstoqueService.explodeKitMovement, o loop "for (KitComponent component : recipe)" que
     * chama adjustStock(...) sem qualquer proteção contra exceção de um componente.
     */
    @Test
    void adjustStock_kit_withInsufficientStockInOneComponent_propagatesExceptionWithoutProcessingRemainingComponents() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2")),
                KitComponent.create("KIT-001", "ESS-001", new BigDecimal("3")),
                KitComponent.create("KIT-001", "CERA-001", BigDecimal.ONE)));
        // CARV-001 tem saldo de sobra: primeiro componente processado com sucesso.
        when(stockBalanceRepository.findBySkuAndWarehouseId("CARV-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "CARV-001", 1L, new BigDecimal("10"), 0L)));
        // ESS-001 só tem 2 disponíveis, mas a receita pede 3 (para 1 kit): estoura aqui.
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(11L, "ESS-001", 1L, new BigDecimal("2"), 0L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.SAIDA,
                BigDecimal.ONE, "Venda balcão", "gerente"))
                .isInstanceOf(InsufficientStockException.class);

        // Primeiro componente já tinha sido processado e persistido antes da falha do segundo.
        verify(stockMovementRepository).save(argThat(m -> m.sku().equals("CARV-001")
                && m.quantity().compareTo(new BigDecimal("2")) == 0));
        verify(stockBalanceRepository).save(argThat(b -> b.sku().equals("CARV-001")));
        // Segundo componente falhou: nunca chega a gravar movimento nem saldo dele.
        verify(stockMovementRepository, never()).save(argThat(m -> m.sku().equals("ESS-001")));
        verify(stockBalanceRepository, never()).save(argThat(b -> b.sku().equals("ESS-001")));
        // Terceiro componente é posterior ao que falhou: o loop nunca chega até ele.
        verify(stockBalanceRepository, never()).findBySkuAndWarehouseId(eq("CERA-001"), any());
        verify(stockMovementRepository, never()).save(argThat(m -> m.sku().equals("CERA-001")));
    }

    @Test
    void defineKitRecipe_promotesProductAndReplacesRecipe() {
        Product product = Product.of(1L, "KIT-001", "Kit Narguile", "combo", true, List.of());
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(product));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", new BigDecimal("2"))));

        assertThat(result.type()).isEqualTo(ProductType.KIT);
        verify(kitComponentRepository).replaceRecipe(eq("KIT-001"), argThat(recipe -> recipe.size() == 1
                && recipe.get(0).componentSku().equals("CARV-001")));
    }

    @Test
    void defineKitRecipe_throwsWhenComponentNotEligible() {
        Product product = Product.of(1L, "KIT-001", "Kit Narguile", "combo", true, List.of());
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(product));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        Product notEligible = Product.of(2L, "CARV-001", "Carvão Coco", "insumo", true, List.of(),
                Pricing.of(new BigDecimal("20.00"), null, new BigDecimal("999.00")));
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(notEligible));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(KitComponentNotEligibleException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenKitSkuNotFound() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenComponentsIsEmpty() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001", List.of()))
                .isInstanceOf(EmptyKitRecipeException.class);
        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001", null))
                .isInstanceOf(EmptyKitRecipeException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenProductHasVariants() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, oneVariant())));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(KitHasVariantsException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenSkuAlreadyComponentOfAnotherKit() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(KitComponentAlreadyInUseException.class);
    }

    @Test
    void defineKitRecipe_throwsOnSelfReference() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("KIT-001", BigDecimal.ONE))))
                .isInstanceOf(KitSelfReferenceException.class);
    }

    @Test
    void defineKitRecipe_throwsOnDuplicateComponent() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        // A primeira ocorrência de CARV-001 precisa resolver normalmente para o laço chegar até
        // a segunda, que é quem de fato aciona a checagem de duplicata.
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001", List.of(
                new KitComponentCommand("CARV-001", BigDecimal.ONE),
                new KitComponentCommand("CARV-001", new BigDecimal("2")))))
                .isInstanceOf(DuplicateKitComponentException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenComponentSkuDoesNotExist() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        when(productRepository.findByAnySku("FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("FANTASMA", BigDecimal.ONE))))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenComponentIsNotSimples() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        when(productRepository.findByAnySku("KIT-002")).thenReturn(Optional.of(kitProduct("KIT-002", "50.00")));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("KIT-002", BigDecimal.ONE))))
                .isInstanceOf(KitComponentNotSimpleException.class);
    }

    @Test
    void getKitRecipe_returnsCurrentRecipe() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(
                List.of(KitComponent.of(5L, "KIT-001", "CARV-001", new BigDecimal("2"))));

        assertThat(estoqueService.getKitRecipe("KIT-001")).hasSize(1);
    }

    @Test
    void getKitRecipe_throwsWhenSkuNotFound() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getKitRecipe("KIT-001"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenComponentInactive() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit Narguile", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        Product inactive = simpleProduct("CARV-001", "20.00").withActive(false);
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(com.cernecommerce.core.domain.exception.estoque.KitComponentInactiveException.class);
    }

    @Test
    void getKitRecipeDetailed_enrichesWithCatalogData() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(
                List.of(KitComponent.of(5L, "KIT-001", "CARV-001", new BigDecimal("2"))));
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));

        List<com.cernecommerce.core.domain.model.estoque.KitComponentDetail> detail =
                estoqueService.getKitRecipeDetailed("KIT-001");

        assertThat(detail).hasSize(1);
        assertThat(detail.get(0).componentSku()).isEqualTo("CARV-001");
        assertThat(detail.get(0).componentName()).isEqualTo("Componente CARV-001");
        assertThat(detail.get(0).componentActive()).isTrue();
    }

    @Test
    void getKitRecipeDetailed_componentMissingFromCatalog_returnsNullFields() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(
                List.of(KitComponent.of(5L, "KIT-001", "SUMIU-001", new BigDecimal("1"))));
        when(productRepository.findByAnySku("SUMIU-001")).thenReturn(Optional.empty());

        List<com.cernecommerce.core.domain.model.estoque.KitComponentDetail> detail =
                estoqueService.getKitRecipeDetailed("KIT-001");

        assertThat(detail.get(0).componentName()).isNull();
        assertThat(detail.get(0).componentActive()).isFalse();
    }

    @Test
    void clearKitRecipe_demotesKitToSimplesAndEmptiesRecipe() {
        Product kit = kitProduct("KIT-001", "80.00");
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(kit));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.clearKitRecipe("KIT-001");

        assertThat(result.type()).isEqualTo(ProductType.SIMPLES);
        verify(kitComponentRepository).replaceRecipe("KIT-001", List.of());
    }

    @Test
    void clearKitRecipe_noOpWhenAlreadySimples() {
        Product simples = Product.of(1L, "SIMP-001", "Produto Simples", "insumo", true, List.of());
        when(productRepository.findBySku("SIMP-001")).thenReturn(Optional.of(simples));

        Product result = estoqueService.clearKitRecipe("SIMP-001");

        assertThat(result.type()).isEqualTo(ProductType.SIMPLES);
        verify(kitComponentRepository, never()).replaceRecipe(any(), any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void clearKitRecipe_throwsWhenSkuNotFound() {
        when(productRepository.findBySku("KIT-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.clearKitRecipe("KIT-FANTASMA"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void createProduct_withKitComponents_persistsRecipeAtomicallyAndPromotesToKit() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.createProduct("KIT-NOVO", "Kit Novo", "combo", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null, null, null, false, false,
                null, null, ProductType.KIT, null, "gerente",
                List.of(new KitComponentCommand("CARV-001", new BigDecimal("2"))));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ProductType.KIT);
        verify(kitComponentRepository).replaceRecipe(eq("KIT-NOVO"), argThat(recipe -> recipe.size() == 1
                && recipe.get(0).componentSku().equals("CARV-001")));
    }

    @Test
    void createProduct_kitWithoutComponents_doesNotTouchRecipe() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.createProduct("KIT-VAZIO", "Kit Vazio", "combo", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null, null, null, false, false,
                null, null, ProductType.KIT, null, "gerente", null);

        verify(kitComponentRepository, never()).replaceRecipe(any(), any());
    }

    @Test
    void createProduct_kitComponents_throwsWhenComponentInactive() {
        Product inactive = simpleProduct("CARV-001", "20.00").withActive(false);
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> estoqueService.createProduct("KIT-NOVO", "Kit Novo", "combo", List.of(),
                Pricing.empty(), null, null, false, false, null, null, List.of(), List.of(), null, null, null,
                false, false, null, null, ProductType.KIT, null, "gerente",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(com.cernecommerce.core.domain.exception.estoque.KitComponentInactiveException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void getKitAvailability_returnsBuildableQuantityAndBlockedFlag() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        Product blockedKit = kitProduct("KIT-BLOQ", "80.00");
        Product buildableKit = kitProduct("KIT-OK", "50.00");
        when(productRepository.findAllByType(ProductType.KIT)).thenReturn(List.of(blockedKit, buildableKit));
        when(kitComponentRepository.findByKitSku("KIT-BLOQ")).thenReturn(
                List.of(KitComponent.of(1L, "KIT-BLOQ", "COMP-001", BigDecimal.ONE)));
        when(kitComponentRepository.findByKitSku("KIT-OK")).thenReturn(
                List.of(KitComponent.of(2L, "KIT-OK", "COMP-002", BigDecimal.ONE)));
        when(stockBalanceRepository.findBySkuAndWarehouseId("COMP-001", 1L)).thenReturn(
                Optional.of(StockBalance.zero("COMP-001", 1L)));
        when(stockBalanceRepository.findBySkuAndWarehouseId("COMP-002", 1L)).thenReturn(
                Optional.of(StockBalance.of(1L, "COMP-002", 1L, new BigDecimal("5"), 0L)));

        PageResult<com.cernecommerce.core.domain.model.estoque.KitAvailability> result =
                estoqueService.getKitAvailability("LOJA-01", null, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).filteredOn(a -> a.kitSku().equals("KIT-BLOQ"))
                .allMatch(a -> a.blocked() && a.buildableQuantity().signum() == 0);
        assertThat(result.content()).filteredOn(a -> a.kitSku().equals("KIT-OK"))
                .allMatch(a -> !a.blocked() && a.buildableQuantity().compareTo(new BigDecimal("5")) == 0);
    }

    @Test
    void getKitAvailability_filtersByBlocked() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        Product blockedKit = kitProduct("KIT-BLOQ", "80.00");
        when(productRepository.findAllByType(ProductType.KIT)).thenReturn(List.of(blockedKit));
        when(kitComponentRepository.findByKitSku("KIT-BLOQ")).thenReturn(List.of());

        PageResult<com.cernecommerce.core.domain.model.estoque.KitAvailability> result =
                estoqueService.getKitAvailability("LOJA-01", false, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void recordCountedItem_rejectsKitSku() {
        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(
                StockCount.open(1L, "gerente")));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));

        assertThatThrownBy(() -> estoqueService.recordCountedItem(1L, "KIT-001", BigDecimal.TEN))
                .isInstanceOf(KitDirectAdjustmentException.class);
    }

    @Test
    void updateProduct_rejectsCostPriceOnKit() {
        Product kit = kitProduct("KIT-001", "80.00");
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(kit));

        assertThatThrownBy(() -> estoqueService.updateProduct("KIT-001", null, null,
                Pricing.of(new BigDecimal("40.00"), null, null)))
                .isInstanceOf(KitCostNotEditableException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_allowsSalePriceChangeOnKit() {
        Product kit = kitProduct("KIT-001", "80.00");
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(kit));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("KIT-001", null, null,
                Pricing.of(null, null, new BigDecimal("90.00")));

        assertThat(updated.pricing().salePrice()).isEqualByComparingTo("90.00");
    }

    @Test
    void setReorderPoint_createsNewWhenNoneExists() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());
        when(reorderPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.setReorderPoint("NARG-001", "LOJA-01", new BigDecimal("10.000"));

        verify(reorderPointRepository).save(argThat(rp -> rp.id() == null
                && rp.sku().equals("NARG-001") && rp.warehouseId().equals(1L)
                && rp.minQuantity().compareTo(new BigDecimal("10.000")) == 0));
    }

    @Test
    void setReorderPoint_updatesExisting() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        ReorderPoint existing = ReorderPoint.of(5L, "NARG-001", 1L, new BigDecimal("5.000"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(reorderPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.setReorderPoint("NARG-001", "LOJA-01", new BigDecimal("10.000"));

        verify(reorderPointRepository).save(argThat(rp -> rp.id().equals(5L)
                && rp.minQuantity().compareTo(new BigDecimal("10.000")) == 0));
    }

    @Test
    void listMovements_resolvesWarehouseCodeAndDelegatesPaging() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockMovement movement = StockMovement.of(9L, "NARG-001", 1L, MovementType.SAIDA, new BigDecimal("2"),
                "Venda balcão sessão #7", "gerente", Instant.parse("2026-07-26T12:00:00Z"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockMovementRepository.findBySkuAndWarehouseId("NARG-001", 1L, null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(movement), 0, 20, 1L, 1));

        PageResult<StockMovement> result = estoqueService.listMovements("NARG-001", "LOJA-01", 0, 20);

        assertThat(result.content()).containsExactly(movement);
        assertThat(result.totalElements()).isEqualTo(1L);
        verify(stockMovementRepository).findBySkuAndWarehouseId("NARG-001", 1L, null, null, null, 0, 20);
    }

    @Test
    void listMovements_returnsEmptyPageWhenSkuNeverMoved() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockMovementRepository.findBySkuAndWarehouseId("SEM-USO", 1L, null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        PageResult<StockMovement> result = estoqueService.listMovements("SEM-USO", "LOJA-01", 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void listMovements_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.listMovements("NARG-001", "INEXISTENTE", 0, 20))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(stockMovementRepository, never()).findBySkuAndWarehouseId(any(), any(), any(), any(), any(),
                anyInt(), anyInt());
    }

    @Test
    void setReorderPoint_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.setReorderPoint("NARG-001", "INEXISTENTE", BigDecimal.TEN))
                .isInstanceOf(WarehouseNotFoundException.class);
        verify(reorderPointRepository, never()).save(any());
    }

    @Test
    void adjustStock_saida_belowReorderPoint_notifiesUsersWithStockManagePermission() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 2L);
        ReorderPoint reorderPoint = ReorderPoint.of(1L, "NARG-001", 1L, new BigDecimal("10.000"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(reorderPoint));
        when(userRepository.findUsernamesByPermission("ESTOQUE_STOCK_MANAGE")).thenReturn(Set.of("gerente-estoque"));

        estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA, new BigDecimal("2.000"),
                "Venda balcão", "vendedor");

        verify(notificationUseCase).notify(eq("gerente-estoque"), eq(NotificationType.SYSTEM), any(), any());
    }

    @Test
    void adjustStock_saida_aboveReorderPoint_doesNotNotify() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 2L);
        ReorderPoint reorderPoint = ReorderPoint.of(1L, "NARG-001", 1L, new BigDecimal("5.000"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(reorderPoint));

        estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA, new BigDecimal("2.000"),
                "Venda balcão", "vendedor");

        verify(notificationUseCase, never()).notify(any(), any(), any(), any());
        verify(userRepository, never()).findUsernamesByPermission(any());
    }

    @Test
    void adjustStock_kitBecomesUnbuildable_notifiesEstoqueOnTransition() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance beforeSaida = StockBalance.of(10L, "COMP-001", 1L, new BigDecimal("1.000"), 0L);
        StockBalance afterSaida = StockBalance.of(10L, "COMP-001", 1L, BigDecimal.ZERO, 1L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        // Duas leituras "antes" (snapshot do kit + saldo atual do próprio adjustStock) e uma
        // "depois" (recálculo do buildable após o save) — mesma ordem de chamada do método real.
        when(stockBalanceRepository.findBySkuAndWarehouseId("COMP-001", 1L))
                .thenReturn(Optional.of(beforeSaida), Optional.of(beforeSaida), Optional.of(afterSaida));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kitComponentRepository.findKitSkusByComponentSku("COMP-001")).thenReturn(List.of("KIT-001"));
        Product kit = kitProduct("KIT-001", "80.00");
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(kit));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(
                List.of(KitComponent.of(1L, "KIT-001", "COMP-001", BigDecimal.ONE)));
        when(userRepository.findUsernamesByPermission("ESTOQUE_STOCK_MANAGE")).thenReturn(Set.of("gerente-estoque"));

        estoqueService.adjustStock("COMP-001", "LOJA-01", MovementType.SAIDA, new BigDecimal("1.000"),
                "Venda balcão", "vendedor");

        verify(notificationUseCase).notify(eq("gerente-estoque"), eq(NotificationType.ESTOQUE), any(), any(),
                eq("/app/estoque/kits/KIT-001"));
    }

    @Test
    void adjustStock_kitAlreadyBlocked_doesNotNotifyAgain() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "COMP-002", 1L, new BigDecimal("5.000"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("COMP-002", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kitComponentRepository.findKitSkusByComponentSku("COMP-002")).thenReturn(List.of("KIT-002"));
        Product kit = kitProduct("KIT-002", "80.00");
        when(productRepository.findBySku("KIT-002")).thenReturn(Optional.of(kit));
        // Receita pede 10 por kit — com 5 disponíveis, buildable já é zero ANTES da baixa, então
        // não há transição >0 -> 0, só continua zero.
        when(kitComponentRepository.findByKitSku("KIT-002")).thenReturn(
                List.of(KitComponent.of(1L, "KIT-002", "COMP-002", BigDecimal.TEN)));

        estoqueService.adjustStock("COMP-002", "LOJA-01", MovementType.SAIDA, new BigDecimal("1.000"),
                "Venda balcão", "vendedor");

        verify(notificationUseCase, never()).notify(any(), eq(NotificationType.ESTOQUE), any(), any(), any());
    }

    @Test
    void adjustStock_throwsWhenSkuNotInCatalog() {
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.adjustStock("SKU-FANTASMA", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente"))
                .isInstanceOf(ProductNotFoundException.class);

        // Nada pode ser gravado — nem ledger, nem saldo. O depósito nem chega a ser resolvido.
        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
        verify(warehouseRepository, never()).findByCode(any());
    }

    @Test
    void adjustStock_acceptsVariantSkuNotJustParentSku() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(productRepository.existsBySku("NARG-M-001")).thenReturn(true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-M-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-M-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente");

        assertThat(result.quantity()).isEqualByComparingTo("5.000");
    }

    @Test
    void setReorderPoint_throwsWhenSkuNotInCatalog() {
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.setReorderPoint("SKU-FANTASMA", "LOJA-01", BigDecimal.TEN))
                .isInstanceOf(ProductNotFoundException.class);

        verify(reorderPointRepository, never()).save(any());
        verify(warehouseRepository, never()).findByCode(any());
    }

    @Test
    void adjustStock_withoutReorderPointConfigured_doesNotNotify() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA, new BigDecimal("5.000"),
                "Recebimento", "gerente");

        verify(notificationUseCase, never()).notify(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------------------------
    // EST-F018 — edição e desativação de produto e depósito
    // ------------------------------------------------------------------------------------

    private Product existingProduct() {
        return Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant());
    }

    @Test
    void updateProduct_alteraApenasOsCamposInformados() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", "Narguilé Aladin 2.0", null);

        assertThat(updated.name()).isEqualTo("Narguilé Aladin 2.0");
        assertThat(updated.category()).as("category nula significa manter").isEqualTo("narguile");
        assertThat(updated.sku()).isEqualTo("NARG-001");
        assertThat(updated.active()).isTrue();
    }

    /** PATCH não pode derrubar a grade: as variações têm que sobreviver à edição. */
    @Test
    void updateProduct_preservaVariacoes() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, "narguile-premium");

        assertThat(updated.name()).as("name nulo significa manter").isEqualTo("Narguile Aladin");
        assertThat(updated.category()).isEqualTo("narguile-premium");
        assertThat(updated.variants()).extracting(ProductVariant::sku).containsExactly("NARG-M-001");
    }

    @Test
    void updateProduct_throwsWhenSkuNotFound() {
        when(productRepository.findBySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.updateProduct("SKU-FANTASMA", "Novo nome", null))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    // ---------- EST-F019 — precificação ----------

    private Product pricedProduct() {
        return Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant(),
                Pricing.of(new BigDecimal("45.00"), new BigDecimal("80"), new BigDecimal("79.90")));
    }

    @Test
    void createProduct_semPricing_nasceNaoPrecificado() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product created = estoqueService.createProduct("CARV-001", "Carvão Coco", "carvao", List.of());

        assertThat(created.pricing().isEmpty()).isTrue();
        assertThat(created.pricing().isPriced()).isFalse();
    }

    @Test
    void createProduct_comPricingNulo_naoQuebra() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product created = estoqueService.createProduct("CARV-001", "Carvão Coco", "carvao", List.of(), null);

        assertThat(created.pricing()).isEqualTo(Pricing.empty());
    }

    @Test
    void createProduct_comMarkup_persisteOPrecoSugerido() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product created = estoqueService.createProduct("NARG-001", "Narguile", "narguile", List.of(),
                Pricing.byMarkup(new BigDecimal("45.00"), new BigDecimal("80")));

        assertThat(created.pricing().effectivePrice()).isEqualByComparingTo("81.00");
        assertThat(created.pricing().marginPercent()).isEqualByComparingTo("44.44");
    }

    @Test
    void updateProduct_pricingNulo_preservaAPrecificacaoAtual() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", "Nome Novo", null, null);

        assertThat(updated.name()).isEqualTo("Nome Novo");
        assertThat(updated.pricing().salePrice()).isEqualByComparingTo("79.90");
        assertThat(updated.pricing().costPrice()).isEqualByComparingTo("45.00");
    }

    /**
     * O caso que motiva o {@code withPatch} no service: mandar só o custo não pode apagar o
     * markup e o preço já cadastrados.
     */
    @Test
    void updateProduct_pricingParcial_naoApagaOsDemaisCampos() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null,
                Pricing.of(new BigDecimal("60.00"), null, null));

        assertThat(updated.pricing().costPrice()).isEqualByComparingTo("60.00");
        assertThat(updated.pricing().markupPercent()).as("markup preservado").isEqualByComparingTo("80");
        assertThat(updated.pricing().salePrice()).as("preço praticado preservado").isEqualByComparingTo("79.90");
        assertThat(updated.pricing().suggestedPrice()).as("sugestão acompanha o custo novo")
                .isEqualByComparingTo("108.00");
    }

    @Test
    void updateProduct_precificaProdutoQueNaoTinhaPreco() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null,
                Pricing.byMarkup(new BigDecimal("45.00"), new BigDecimal("80")));

        assertThat(updated.pricing().effectivePrice()).isEqualByComparingTo("81.00");
    }

    @Test
    void updateProduct_pricingNaoDerrubaAsVariacoes() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null,
                Pricing.of(new BigDecimal("60.00"), null, null));

        assertThat(updated.variants()).extracting(ProductVariant::sku).containsExactly("NARG-M-001");
    }

    @Test
    void findPricingBySku_resolvePeloSkuPai() {
        when(productRepository.findByAnySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));

        assertThat(estoqueService.findPricingBySku("NARG-001").effectivePrice())
                .isEqualByComparingTo("79.90");
    }

    // ── resolveSaleInfo (productName no item do pedido, BACKEND_TODO.md do mahal-admin) ──────

    @Test
    void resolveSaleInfo_devolveNomeEPrecoNumaConsultaSo() {
        when(productRepository.findByAnySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));

        CatalogSaleInfo info = estoqueService.resolveSaleInfo("NARG-001");

        assertThat(info.productName()).isEqualTo("Narguile Aladin");
        assertThat(info.pricing().effectivePrice()).isEqualByComparingTo("79.90");
        verify(productRepository, times(1)).findByAnySku("NARG-001");
    }

    @Test
    void resolveSaleInfo_kit_derivaPrecoMasUsaOProprioNome() {
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2"))));
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));

        CatalogSaleInfo info = estoqueService.resolveSaleInfo("KIT-001");

        assertThat(info.productName()).isEqualTo("Kit KIT-001");
        assertThat(info.pricing().costPrice()).isEqualByComparingTo("40.00");
        assertThat(info.pricing().salePrice()).isEqualByComparingTo("80.00");
    }

    @Test
    void resolveSaleInfo_throwsWhenSkuNotInCatalog() {
        when(productRepository.findByAnySku("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.resolveSaleInfo("GHOST"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ---------- Campos de marketing: superPromo, description, videoUrl, images ----------

    @Test
    void createProduct_persisteOsCincoCamposDeMarketing() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product created = estoqueService.createProduct("NARG-001", "Narguile", "narguile", List.of(),
                Pricing.of(new BigDecimal("45.00"), null, new BigDecimal("79.90"), new BigDecimal("99.90")),
                "Aladin", "http://img.png", true, true, "Descrição longa", "http://video.mp4",
                List.of("http://img1.png", "http://img2.png"));

        assertThat(created.pricing().originalPrice()).isEqualByComparingTo("99.90");
        assertThat(created.superPromo()).isTrue();
        assertThat(created.description()).isEqualTo("Descrição longa");
        assertThat(created.videoUrl()).isEqualTo("http://video.mp4");
        assertThat(created.images()).containsExactly("http://img1.png", "http://img2.png");
    }

    @Test
    void updateProduct_alteraSuperPromoIndependentemente() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null, null, null, null, null,
                true, null, null, null);

        assertThat(updated.superPromo()).isTrue();
        assertThat(updated.name()).as("demais campos preservados").isEqualTo("Narguile Aladin");
    }

    @Test
    void updateProduct_superPromoNulo_preservaOValorAtual() {
        Product marcado = existingProduct().withSuperPromo(true);
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(marcado));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", "Novo nome", null, null, null, null, null,
                null, null, null, null);

        assertThat(updated.superPromo()).as("nulo mantém o valor atual").isTrue();
    }

    @Test
    void updateProduct_alteraDescriptionEVideoUrlIndependentemente() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null, null, null, null, null,
                null, "Nova descrição", "http://video.mp4", null);

        assertThat(updated.description()).isEqualTo("Nova descrição");
        assertThat(updated.videoUrl()).isEqualTo("http://video.mp4");
        assertThat(updated.name()).as("demais campos preservados").isEqualTo("Narguile Aladin");
    }

    @Test
    void updateProduct_descriptionEVideoUrlNulos_preservamOValorAtual() {
        Product comDetalhes = existingProduct().withDetails(null, null, null, null, "Descrição", "http://video.mp4");
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(comDetalhes));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", "Novo nome", null, null, null, null, null,
                null, null, null, null);

        assertThat(updated.description()).isEqualTo("Descrição");
        assertThat(updated.videoUrl()).isEqualTo("http://video.mp4");
    }

    @Test
    void updateProduct_substituiAGaleriaDeImagens() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null, null, null, null, null,
                null, null, null, List.of("http://img1.png", "http://img2.png"));

        assertThat(updated.images()).containsExactly("http://img1.png", "http://img2.png");
    }

    @Test
    void updateProduct_imagesNulo_preservaAGaleriaAtual() {
        Product comGaleria = existingProduct().withImages(List.of("http://img1.png"));
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(comGaleria));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", "Novo nome", null, null, null, null, null,
                null, null, null, null);

        assertThat(updated.images()).containsExactly("http://img1.png");
    }

    @Test
    void updateProduct_imagesListaVazia_limpaAGaleria() {
        Product comGaleria = existingProduct().withImages(List.of("http://img1.png"));
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(comGaleria));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null, null, null, null, null,
                null, null, null, List.of());

        assertThat(updated.images()).isEmpty();
    }

    @Test
    void updateProduct_pricingCarregaOriginalPrice() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null,
                Pricing.of(null, null, null, new BigDecimal("99.90")));

        assertThat(updated.pricing().originalPrice()).isEqualByComparingTo("99.90");
        assertThat(updated.pricing().salePrice()).as("preço praticado preservado")
                .isEqualByComparingTo("79.90");
    }

    /** A variação herda o preço do pai — é o caminho do leitor de código de barras no balcão. */
    @Test
    void findPricingBySku_resolveVariacaoPeloPrecoDoPai() {
        when(productRepository.findByAnySku("NARG-M-001")).thenReturn(Optional.of(pricedProduct()));

        assertThat(estoqueService.findPricingBySku("NARG-M-001").effectivePrice())
                .isEqualByComparingTo("79.90");
    }

    @Test
    void findPricingBySku_produtoSemPreco_devolveEmptyEmVezDeErro() {
        when(productRepository.findByAnySku("NARG-001")).thenReturn(Optional.of(existingProduct()));

        Pricing pricing = estoqueService.findPricingBySku("NARG-001");

        assertThat(pricing.isEmpty()).isTrue();
        assertThat(pricing.isPriced()).isFalse();
    }

    @Test
    void findPricingBySku_throwsWhenSkuNotInCatalog() {
        when(productRepository.findByAnySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.findPricingBySku("SKU-FANTASMA"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void setProductActive_desativaPreservandoORestante() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.setProductActive("NARG-001", false);

        assertThat(updated.active()).isFalse();
        assertThat(updated.name()).isEqualTo("Narguile Aladin");
        assertThat(updated.variants()).hasSize(1);
    }

    @Test
    void setProductActive_throwsWhenSkuNotFound() {
        when(productRepository.findBySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.setProductActive("SKU-FANTASMA", false))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void setProductLotTracked_ativaPreservandoORestante() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.setProductLotTracked("NARG-001", true);

        assertThat(updated.lotTracked()).isTrue();
        assertThat(updated.name()).isEqualTo("Narguile Aladin");
        assertThat(updated.variants()).hasSize(1);
    }

    @Test
    void setProductLotTracked_throwsWhenSkuNotFound() {
        when(productRepository.findBySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.setProductLotTracked("SKU-FANTASMA", true))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void setProductLotTracked_emKit_lancaIllegalArgument() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));

        assertThatThrownBy(() -> estoqueService.setProductLotTracked("KIT-001", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kit não pode ser lote-rastreado");

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateWarehouse_alteraApenasOsCamposInformados() {
        Warehouse current = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(current));
        when(warehouseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Warehouse updated = estoqueService.updateWarehouse("LOJA-01", "Loja Centro Reformada", null);

        assertThat(updated.name()).isEqualTo("Loja Centro Reformada");
        assertThat(updated.type()).as("type nulo significa manter").isEqualTo(WarehouseType.LOJA_FISICA);
        assertThat(updated.code()).isEqualTo("LOJA-01");
    }

    @Test
    void updateWarehouse_throwsWhenNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.updateWarehouse("INEXISTENTE", "Nome", null))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void setWarehouseActive_desativa() {
        Warehouse current = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(current));
        when(warehouseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(estoqueService.setWarehouseActive("LOJA-01", false).active()).isFalse();
    }

    // ---- Efeito da desativação sobre a movimentação ----

    @Test
    void adjustStock_entrada_emSkuDesativado_throwsAndDoesNotPersistAnything() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.isSkuActive("NARG-001")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente"))
                .isInstanceOf(InactiveProductException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void adjustStock_entrada_emDepositoDesativado_throwsAndDoesNotPersistAnything() {
        Warehouse inativo = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, false);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente"))
                .isInstanceOf(InactiveWarehouseException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    /** O ponto da decisão: desativar não pode prender o saldo que ainda está na prateleira. */
    @Test
    void adjustStock_saida_emSkuDesativado_continuaPermitida() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(7L, "NARG-001", 1L, new BigDecimal("10.000"), 3L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("4.000"), "Venda", "gerente");

        assertThat(result.quantity()).isEqualByComparingTo("6.000");
        verify(productRepository, never()).isSkuActive(any());
    }

    /** AJUSTE é o caminho de correção de inventário — não pode ser barrado por desativação. */
    @Test
    void adjustStock_ajuste_emSkuDesativado_continuaPermitido() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(7L, "NARG-001", 1L, new BigDecimal("10.000"), 3L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.AJUSTE,
                new BigDecimal("2.000"), "Contagem", "gerente");

        assertThat(result.quantity()).as("EST-C009: AJUSTE é saldo-alvo, não delta")
                .isEqualByComparingTo("2.000");
        verify(productRepository, never()).isSkuActive(any());
    }

    /** SKU inexistente tem precedência: 404 antes de qualquer checagem de estado. */
    @Test
    void adjustStock_entrada_skuDesconhecido_temPrecedenciaSobreDesativado() {
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.adjustStock("SKU-FANTASMA", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).isSkuActive(any());
    }

    @Test
    void listOrphanSkus_delegatesPagingToTheIntegrityRepository() {
        OrphanSku orphan = OrphanSku.of("SKU-FANTASMA", "LOJA-01", new BigDecimal("3.000"), 2L, false,
                Instant.parse("2026-07-01T10:00:00Z"));
        when(stockIntegrityRepository.findOrphanSkus(1, 50))
                .thenReturn(new PageResult<>(List.of(orphan), 1, 50, 1L, 1));

        PageResult<OrphanSku> result = estoqueService.listOrphanSkus(1, 50);

        assertThat(result.content()).containsExactly(orphan);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        verify(stockIntegrityRepository).findOrphanSkus(1, 50);
    }

    /** Base íntegra é o caso esperado depois de EST-C002; não é erro nem 404. */
    @Test
    void listOrphanSkus_returnsEmptyPageWhenBaseIsClean() {
        when(stockIntegrityRepository.findOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        PageResult<OrphanSku> result = estoqueService.listOrphanSkus(0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    /** É diagnóstico: não pode escrever nada, nem sequer resolver depósito por código. */
    @Test
    void listOrphanSkus_doesNotTouchAnyWriteRepository() {
        when(stockIntegrityRepository.findOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        estoqueService.listOrphanSkus(0, 20);

        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(reorderPointRepository, never()).save(any());
        verify(warehouseRepository, never()).findByCode(any());
    }

    // ── Listagem e integridade de lote (EST-F008) ────────────────────────────────────────

    @Test
    void listStockLots_delegaParaORepositorioDeLotes() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(
                Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", LocalDate.of(2027, 1, 1),
                new BigDecimal("5.000"), null, 0L);
        when(stockLotRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(List.of(lot));

        List<StockLot> result = estoqueService.listStockLots("ESS-001", "LOJA-01");

        assertThat(result).containsExactly(lot);
    }

    @Test
    void listStockLots_semLoteRecebido_retornaListaVazia() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(
                Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));
        when(stockLotRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(List.of());

        assertThat(estoqueService.listStockLots("ESS-001", "LOJA-01")).isEmpty();
    }

    @Test
    void listStockLots_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.listStockLots("ESS-001", "INEXISTENTE"))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void listLotMismatches_delegatesPagingToTheIntegrityRepository() {
        LotIntegrityMismatch mismatch = LotIntegrityMismatch.of("ESS-999", "LOJA-01",
                new BigDecimal("5.000"), new BigDecimal("3.000"));
        when(stockIntegrityRepository.findLotMismatches(1, 50))
                .thenReturn(new PageResult<>(List.of(mismatch), 1, 50, 1L, 1));

        PageResult<LotIntegrityMismatch> result = estoqueService.listLotMismatches(1, 50);

        assertThat(result.content()).containsExactly(mismatch);
        verify(stockIntegrityRepository).findLotMismatches(1, 50);
    }

    /** Base íntegra é o caso esperado; não é erro. */
    @Test
    void listLotMismatches_returnsEmptyPageWhenBaseIsClean() {
        when(stockIntegrityRepository.findLotMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        PageResult<LotIntegrityMismatch> result = estoqueService.listLotMismatches(0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    // ------------------------------------------------------------------------------------
    // EST-F006 — balanço de inventário
    // ------------------------------------------------------------------------------------

    private static final Warehouse LOJA =
            Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);

    private StockCount openCount(List<StockCountItem> items) {
        return StockCount.of(50L, 1L, StockCountStatus.ABERTA, "gerente",
                Instant.parse("2026-07-27T09:00:00Z"), null, items);
    }

    @Test
    void openStockCount_criaBalancoAbertoParaODeposito() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockCountRepository.findOpenByWarehouseId(1L)).thenReturn(Optional.empty());
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount opened = estoqueService.openStockCount("LOJA-01", "gerente");

        assertThat(opened.status()).isEqualTo(StockCountStatus.ABERTA);
        assertThat(opened.warehouseId()).isEqualTo(1L);
        assertThat(opened.username()).isEqualTo("gerente");
        assertThat(opened.items()).isEmpty();
        assertThat(opened.closedAt()).isNull();
    }

    /** Dois balanços no mesmo depósito contariam o mesmo saldo e se sobrescreveriam no fechamento. */
    @Test
    void openStockCount_recusaSegundoBalancoAbertoNoMesmoDeposito() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockCountRepository.findOpenByWarehouseId(1L)).thenReturn(Optional.of(openCount(List.of())));

        assertThatThrownBy(() -> estoqueService.openStockCount("LOJA-01", "gerente"))
                .isInstanceOf(StockCountAlreadyOpenException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void openStockCount_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.openStockCount("INEXISTENTE", "gerente"))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void recordCountedItem_registraOContado() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount updated = estoqueService.recordCountedItem(50L, "NARG-001", new BigDecimal("37.000"));

        // EST-C0xx: o registro já confronta contra o saldo do sistema NESTE instante — não espera
        // o fechamento — para que o fechamento só precise aplicar a divergência sobre o saldo
        // atual, sem apagar movimentação legítima que aconteça entre o registro e o fechamento.
        assertThat(updated.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("NARG-001");
            assertThat(item.countedQuantity()).isEqualByComparingTo("37.000");
            assertThat(item.expectedQuantity()).as("registro confronta contra saldo zero implícito")
                    .isEqualByComparingTo("0");
            assertThat(item.difference()).isEqualByComparingTo("37.000");
        });
    }

    /** Recontagem é o caso normal num balanço: sobrescreve, não vira segunda linha. */
    @Test
    void recordCountedItem_recontarSobrescreve() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(
                openCount(List.of(StockCountItem.of(9L, "NARG-001", new BigDecimal("30.000"), null, null)))));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount updated = estoqueService.recordCountedItem(50L, "NARG-001", new BigDecimal("37.000"));

        assertThat(updated.items()).singleElement().satisfies(item -> {
            assertThat(item.countedQuantity()).isEqualByComparingTo("37.000");
            assertThat(item.id()).as("reaproveita a linha existente").isEqualTo(9L);
        });
    }

    @Test
    void recordCountedItem_throwsWhenSkuNotInCatalog() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.recordCountedItem(50L, "SKU-FANTASMA", BigDecimal.TEN))
                .isInstanceOf(ProductNotFoundException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void recordCountedItem_throwsWhenCountNotFound() {
        when(stockCountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.recordCountedItem(99L, "NARG-001", BigDecimal.TEN))
                .isInstanceOf(StockCountNotFoundException.class);
    }

    @Test
    void recordCountedItem_throwsWhenCountAlreadyClosed() {
        StockCount fechado = StockCount.of(50L, 1L, StockCountStatus.FECHADA, "gerente",
                Instant.now(), Instant.now(), List.of());
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(fechado));

        assertThatThrownBy(() -> estoqueService.recordCountedItem(50L, "NARG-001", BigDecimal.TEN))
                .isInstanceOf(StockCountNotOpenException.class);

        verify(stockCountRepository, never()).save(any());
    }

    /**
     * O coração do F006: divergência vira AJUSTE. Os itens já chegam reconciliados (é o que
     * {@code recordCountedItem} faz desde EST-C0xx) — o saldo mockado aqui é o mesmo no registro
     * e no fechamento (sem movimentação concorrente), então o AJUSTE final bate com o valor
     * contado direto, como sempre bateu. O teste de saldo divergindo entre registro e fechamento
     * é {@code closeStockCount_movimentacaoEntreRegistroEFechamento_aplicaDeltaNaoSubstitui}.
     */
    @Test
    void closeStockCount_aplicaAjusteApenasNosItensDivergentes() {
        StockCount count = openCount(List.of(
                StockCountItem.of(1L, "SKU-FALTA", new BigDecimal("8.000"), new BigDecimal("10.000"), new BigDecimal("-2.000")),
                StockCountItem.of(2L, "SKU-BATEU", new BigDecimal("5.000"), new BigDecimal("5.000"), new BigDecimal("0.000")),
                StockCountItem.of(3L, "SKU-SOBRA", new BigDecimal("12.000"), new BigDecimal("9.000"), new BigDecimal("3.000"))));
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(count));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-FALTA", 1L))
                .thenReturn(Optional.of(StockBalance.of(1L, "SKU-FALTA", 1L, new BigDecimal("10.000"), 0L)));
        // SKU-BATEU não diverge (difference=0.000, já carimbado no item) — closeAggregateSku nem
        // chega a consultar o saldo dele, então nenhum stub aqui (stub não usado quebraria a
        // checagem estrita do Mockito).
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-SOBRA", 1L))
                .thenReturn(Optional.of(StockBalance.of(3L, "SKU-SOBRA", 1L, new BigDecimal("9.000"), 0L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount closed = estoqueService.closeStockCount(50L, "gerente");

        assertThat(closed.status()).isEqualTo(StockCountStatus.FECHADA);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.items()).extracting(StockCountItem::sku, StockCountItem::expectedQuantity,
                        StockCountItem::difference)
                .containsExactly(
                        tuple("SKU-FALTA", new BigDecimal("10.000"), new BigDecimal("-2.000")),
                        tuple("SKU-BATEU", new BigDecimal("5.000"), new BigDecimal("0.000")),
                        tuple("SKU-SOBRA", new BigDecimal("9.000"), new BigDecimal("3.000")));

        // Contagem que bateu não polui o ledger: só dois movimentos, não três.
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(m ->
                assertThat(m.type()).isEqualTo(MovementType.AJUSTE));
        assertThat(captor.getAllValues()).extracting(StockMovement::sku, StockMovement::quantity)
                .containsExactly(
                        tuple("SKU-FALTA", new BigDecimal("8.000")),
                        tuple("SKU-SOBRA", new BigDecimal("12.000")));
        assertThat(captor.getAllValues()).allSatisfy(m ->
                assertThat(m.reason()).contains("Balanço de inventário #50"));
    }

    /**
     * EST-C0xx: prova o fix do bug de snapshot. No registro, o contador viu 10 no sistema e
     * contou 8 (faltam 2) — {@code difference = -2} carimbado ali. Antes do fechamento, uma
     * ENTRADA de +5 chega (recebimento de mercadoria, concorrente com o balanço ainda aberto):
     * saldo atual do sistema vira 15. O AJUSTE do fechamento tem que somar a divergência de -2
     * ao saldo ATUAL (15 - 2 = 13), não substituir pelo valor contado direto (8) — 8 apagaria a
     * entrada de +5 que aconteceu depois da contagem, como se ela nunca tivesse existido.
     */
    @Test
    void closeStockCount_movimentacaoEntreRegistroEFechamento_aplicaDeltaNaoSubstitui() {
        StockCount count = openCount(List.of(
                StockCountItem.of(1L, "SKU-DRIFT", new BigDecimal("8.000"), new BigDecimal("10.000"), new BigDecimal("-2.000"))));
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(count));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        // Saldo ATUAL no fechamento já reflete a ENTRADA concorrente de +5 (10 + 5 = 15) —
        // diferente do saldo de 10 que estava carimbado no item quando ele foi contado.
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-DRIFT", 1L))
                .thenReturn(Optional.of(StockBalance.of(1L, "SKU-DRIFT", 1L, new BigDecimal("15.000"), 0L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.closeStockCount(50L, "gerente");

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().quantity())
                .as("13 (saldo atual 15 + divergência -2), não 8 (o valor contado, que apagaria a entrada concorrente)")
                .isEqualByComparingTo("13.000");

        ArgumentCaptor<StockBalance> balanceCaptor = ArgumentCaptor.forClass(StockBalance.class);
        verify(stockBalanceRepository).save(balanceCaptor.capture());
        assertThat(balanceCaptor.getValue().quantity()).isEqualByComparingTo("13.000");
    }

    /** SKU nunca movimentado tem saldo zero implícito — contar 4 ali é sobra de 4. */
    @Test
    void closeStockCount_skuSemSaldoRegistrado_confrontaContraZero() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(
                List.of(StockCountItem.of(1L, "SKU-NOVO", new BigDecimal("4.000"),
                        BigDecimal.ZERO, new BigDecimal("4.000"))))));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-NOVO", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount closed = estoqueService.closeStockCount(50L, "gerente");

        assertThat(closed.items()).singleElement().satisfies(item -> {
            assertThat(item.expectedQuantity()).isEqualByComparingTo("0");
            assertThat(item.difference()).isEqualByComparingTo("4.000");
        });
    }

    /** Contar zero é o caso do item que sumiu — e o AJUSTE tem que conseguir zerar o saldo. */
    @Test
    void closeStockCount_contagemZero_zeraOSaldo() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(
                List.of(StockCountItem.of(1L, "SKU-SUMIU", BigDecimal.ZERO,
                        new BigDecimal("6.000"), new BigDecimal("-6.000"))))));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-SUMIU", 1L))
                .thenReturn(Optional.of(StockBalance.of(1L, "SKU-SUMIU", 1L, new BigDecimal("6.000"), 0L)));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<StockBalance> captor = ArgumentCaptor.forClass(StockBalance.class);
        when(stockBalanceRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.closeStockCount(50L, "gerente");

        assertThat(captor.getValue().quantity()).isEqualByComparingTo("0");
    }

    @Test
    void closeStockCount_semItens_fechaSemMovimentar() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(estoqueService.closeStockCount(50L, "gerente").status())
                .isEqualTo(StockCountStatus.FECHADA);

        verify(stockMovementRepository, never()).save(any());
    }

    /** Fechar duas vezes aplicaria o mesmo ajuste em dobro. */
    @Test
    void closeStockCount_balancoJaFechado_throwsAndDoesNotAdjust() {
        StockCount fechado = StockCount.of(50L, 1L, StockCountStatus.FECHADA, "gerente",
                Instant.now(), Instant.now(), List.of());
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(fechado));

        assertThatThrownBy(() -> estoqueService.closeStockCount(50L, "gerente"))
                .isInstanceOf(StockCountNotOpenException.class);

        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void cancelStockCount_naoTocaEmSaldo() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(
                List.of(StockCountItem.of(1L, "NARG-001", new BigDecimal("3.000"), null, null)))));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount cancelled = estoqueService.cancelStockCount(50L);

        assertThat(cancelled.status()).isEqualTo(StockCountStatus.CANCELADA);
        assertThat(cancelled.closedAt()).isNotNull();
        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    // ── Balanço por lote (EST-F008) ──────────────────────────────────────────────────────────

    @Test
    void recordCountedItem_loteRastreado_semLoteCode_lancaMissingLotInfo() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));

        assertThatThrownBy(() -> estoqueService.recordCountedItem(50L, "ESS-001", new BigDecimal("5.000")))
                .isInstanceOf(MissingLotInfoException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void recordCountedItem_naoLoteRastreado_comLoteCode_lancaUnexpectedLotInfo() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));

        assertThatThrownBy(() -> estoqueService.recordCountedItem(50L, "NARG-001", new BigDecimal("5.000"), "LOTE-A"))
                .isInstanceOf(UnexpectedLotInfoException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void recordCountedItem_loteInexistente_lancaStockLotNotFound() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));
        when(stockLotRepository.findBySkuAndWarehouseIdAndLotCode("ESS-001", 1L, "LOTE-FANTASMA"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.recordCountedItem(50L, "ESS-001", new BigDecimal("5.000"),
                "LOTE-FANTASMA"))
                .isInstanceOf(StockLotNotFoundException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void recordCountedItem_loteRastreado_comLoteExistente_registraOContadoPorLote() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", LocalDate.of(2027, 3, 1),
                new BigDecimal("10.000"), null, 0L);
        when(stockLotRepository.findBySkuAndWarehouseIdAndLotCode("ESS-001", 1L, "LOTE-A"))
                .thenReturn(Optional.of(lot));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount updated = estoqueService.recordCountedItem(50L, "ESS-001", new BigDecimal("7.000"), "LOTE-A");

        assertThat(updated.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("ESS-001");
            assertThat(item.lotCode()).isEqualTo("LOTE-A");
            assertThat(item.countedQuantity()).isEqualByComparingTo("7.000");
        });
    }

    /**
     * Cada lote reconcilia contra o próprio saldo, não contra o agregado: LOTE-A divergiu (tinha
     * 4, contaram 3) e é ajustado por {@code reconciledTo}; LOTE-B bateu (tinha 5, contaram 5) e
     * não é salvo de novo. Só depois disso o agregado ganha UM AJUSTE para a soma dos lotes
     * contados (8.000), não um por lote.
     */
    @Test
    void closeStockCount_loteRastreado_reconciliaCadaLoteEAgregado() {
        StockCount count = openCount(List.of(
                StockCountItem.of(1L, "ESS-001", new BigDecimal("3.000"),
                        new BigDecimal("4.000"), new BigDecimal("-1.000"), "LOTE-A"),
                StockCountItem.of(2L, "ESS-001", new BigDecimal("5.000"),
                        new BigDecimal("5.000"), new BigDecimal("0.000"), "LOTE-B")));
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(count));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));

        // LOTE-B não diverge (difference=0.000, carimbado no registro) — o fechamento nem chega a
        // buscar esse StockLot, então não há stub de findBySkuAndWarehouseIdAndLotCode para ele
        // (stub não usado quebraria a checagem estrita do Mockito).
        StockLot loteA = StockLot.of(10L, "ESS-001", 1L, "LOTE-A", LocalDate.of(2027, 1, 1),
                new BigDecimal("4.000"), null, 0L);
        when(stockLotRepository.findBySkuAndWarehouseIdAndLotCode("ESS-001", 1L, "LOTE-A"))
                .thenReturn(Optional.of(loteA));
        when(stockLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(20L, "ESS-001", 1L, new BigDecimal("9.000"), 0L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount closed = estoqueService.closeStockCount(50L, "gerente");

        verify(stockLotRepository).save(argThat(l -> l.lotCode().equals("LOTE-A")
                && l.quantity().compareTo(new BigDecimal("3.000")) == 0));
        verify(stockLotRepository, never()).save(argThat(l -> l.lotCode().equals("LOTE-B")));

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(MovementType.AJUSTE);
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("8.000");

        assertThat(closed.items()).extracting(StockCountItem::lotCode, StockCountItem::expectedQuantity,
                        StockCountItem::difference)
                .containsExactlyInAnyOrder(
                        tuple("LOTE-A", new BigDecimal("4.000"), new BigDecimal("-1.000")),
                        tuple("LOTE-B", new BigDecimal("5.000"), new BigDecimal("0.000")));
    }

    /**
     * Itens já chegam reconciliados sem divergência (é o que {@code recordCountedItem} carimba
     * quando o contado bate com o saldo do sistema) — o fechamento nem chega a buscar o
     * {@code StockLot} de cada um, porque {@code item.diverges()} já responde a pergunta sem
     * precisar reconsultar nada (EST-C0xx).
     */
    @Test
    void closeStockCount_loteRastreado_semDivergencia_naoGeraAjuste() {
        StockCount count = openCount(List.of(
                StockCountItem.of(1L, "ESS-001", new BigDecimal("4.000"),
                        new BigDecimal("4.000"), new BigDecimal("0.000"), "LOTE-A"),
                StockCountItem.of(2L, "ESS-001", new BigDecimal("5.000"),
                        new BigDecimal("5.000"), new BigDecimal("0.000"), "LOTE-B")));
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(count));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));

        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(20L, "ESS-001", 1L, new BigDecimal("9.000"), 0L)));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.closeStockCount(50L, "gerente");

        verify(stockLotRepository, never()).findBySkuAndWarehouseIdAndLotCode(any(), any(), any());
        verify(stockLotRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void getStockCount_throwsWhenNotFound() {
        when(stockCountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getStockCount(99L))
                .isInstanceOf(StockCountNotFoundException.class);
    }

    @Test
    void listStockCounts_resolveODepositoEDelegaPaginacao() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockCountRepository.findByWarehouseId(1L, 0, 20))
                .thenReturn(new PageResult<>(List.of(openCount(List.of())), 0, 20, 1L, 1));

        assertThat(estoqueService.listStockCounts("LOJA-01", 0, 20).content()).hasSize(1);
        verify(stockCountRepository).findByWarehouseId(1L, 0, 20);
    }

    @Test
    void listStockCounts_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.listStockCounts("INEXISTENTE", 0, 20))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    // ------------------------------------------------------------------------------------
    // EST-F013/F021 — reserva de estoque
    // ------------------------------------------------------------------------------------

    private StockReservation activeReservation(Long id, String sku, Long warehouseId, String quantity,
            String ownerReference) {
        return StockReservation.of(id, sku, warehouseId, new BigDecimal(quantity), ownerReference,
                ReservationStatus.ACTIVE, Instant.parse("2026-07-30T13:00:00Z"),
                Instant.parse("2026-07-30T12:30:00Z"), null, "checkout");
    }

    @Test
    void reserveStock_semSaldoAnterior_lancaInsufficientStockENaoCriaReserva() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.reserveStock("NARG-001", "LOJA-01", new BigDecimal("2.000"),
                "CHECKOUT-1", null, "checkout"))
                .isInstanceOf(InsufficientStockException.class);
        verify(stockBalanceRepository, never()).save(any());
        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void reserveStock_comSaldoDisponivel_criaReservaAtiva() {
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockReservation result = estoqueService.reserveStock("NARG-001", "LOJA-01", new BigDecimal("2.000"),
                "CHECKOUT-1", null, "checkout");

        assertThat(result.sku()).isEqualTo("NARG-001");
        assertThat(result.warehouseId()).isEqualTo(1L);
        assertThat(result.quantity()).isEqualByComparingTo("2.000");
        assertThat(result.ownerReference()).isEqualTo("CHECKOUT-1");
        assertThat(result.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(result.username()).isEqualTo("checkout");
        verify(stockBalanceRepository).save(argThat(b -> b.reservedQuantity().compareTo(new BigDecimal("2.000")) == 0));
    }

    @Test
    void reserveStock_semTtlInformado_usaODefault() {
        Instant before = Instant.now();
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockReservation result = estoqueService.reserveStock("NARG-001", "LOJA-01", new BigDecimal("2.000"),
                "CHECKOUT-1", null, "checkout");

        assertThat(result.expiresAt()).isBetween(before.plus(RESERVATION_TTL), Instant.now().plus(RESERVATION_TTL));
    }

    @Test
    void reserveStock_comTtlInformado_sobrescreveODefault() {
        Instant before = Instant.now();
        Duration customTtl = Duration.ofHours(2);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockReservation result = estoqueService.reserveStock("NARG-001", "LOJA-01", new BigDecimal("2.000"),
                "CHECKOUT-1", customTtl, "checkout");

        assertThat(result.expiresAt()).isBetween(before.plus(customTtl), Instant.now().plus(customTtl));
    }

    @Test
    void reserveStock_throwsWhenSkuDesconhecido() {
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.reserveStock("SKU-FANTASMA", "LOJA-01", BigDecimal.ONE,
                "CHECKOUT-1", null, "checkout"))
                .isInstanceOf(ProductNotFoundException.class);
        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void reserveStock_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.reserveStock("NARG-001", "INEXISTENTE", BigDecimal.ONE,
                "CHECKOUT-1", null, "checkout"))
                .isInstanceOf(WarehouseNotFoundException.class);
        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void reserveStock_acimaDoDisponivel_lancaInsufficientStockENaoCriaReserva() {
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("5.000"),
                new BigDecimal("4.000"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> estoqueService.reserveStock("NARG-001", "LOJA-01", new BigDecimal("2.000"),
                "CHECKOUT-1", null, "checkout"))
                .isInstanceOf(InsufficientStockException.class);
        verify(stockBalanceRepository, never()).save(any());
        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void reserveStock_disponivelAbaixoDoPontoDeReposicao_notifica() {
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);
        ReorderPoint reorderPoint = ReorderPoint.of(1L, "NARG-001", 1L, new BigDecimal("9.000"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(reorderPoint));
        when(userRepository.findUsernamesByPermission("ESTOQUE_STOCK_MANAGE")).thenReturn(Set.of("gerente-estoque"));

        estoqueService.reserveStock("NARG-001", "LOJA-01", new BigDecimal("2.000"), "CHECKOUT-1", null, "checkout");

        verify(notificationUseCase).notify(eq("gerente-estoque"), eq(NotificationType.SYSTEM), any(), any());
    }

    @Test
    void consumeReservation_baixaSaldoRegistraMovimentoEMarcaConsumida() {
        StockReservation reservation = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1");
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);
        when(stockReservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockReservation result = estoqueService.consumeReservation(1L, "caixa");

        assertThat(result.status()).isEqualTo(ReservationStatus.CONSUMED);
        verify(stockBalanceRepository).save(argThat(b -> b.quantity().compareTo(new BigDecimal("8.000")) == 0
                && b.reservedQuantity().signum() == 0));
        verify(stockMovementRepository).save(argThat(m -> m.type() == MovementType.SAIDA
                && m.quantity().compareTo(new BigDecimal("2.000")) == 0
                && m.reason().contains("#1")
                && m.username().equals("caixa")));
        verifyNoInteractions(stockLotRepository);
    }

    /**
     * EST-F008: este caminho não passa por adjustStock (a reserva já descontou o disponível), então
     * o consumo FEFO precisa disparar aqui também — senão a venda do marketplace nunca desconta
     * stock_lot, e o saldo por lote fica descolado do agregado.
     */
    @Test
    void consumeReservation_loteRastreado_consomeFefo() {
        StockReservation reservation = activeReservation(1L, "ESS-001", 1L, "2.000", "CHECKOUT-1");
        StockBalance existing = StockBalance.of(10L, "ESS-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", LocalDate.of(2027, 3, 1),
                new BigDecimal("5.000"), null, 0L);
        when(stockReservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(lotTrackedProduct("ESS-001")));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockLotRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(List.of(lot));
        when(stockLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.consumeReservation(1L, "caixa");

        verify(stockLotRepository).save(argThat(l -> l.lotCode().equals("LOTE-A")
                && l.quantity().compareTo(new BigDecimal("3.000")) == 0));
    }

    @Test
    void consumeReservation_throwsWhenReservaNaoExiste() {
        when(stockReservationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.consumeReservation(99L, "caixa"))
                .isInstanceOf(StockReservationNotFoundException.class);
        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void consumeReservation_throwsWhenReservaJaNaoEstaAtiva() {
        StockReservation released = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1").released();
        when(stockReservationRepository.findById(1L)).thenReturn(Optional.of(released));

        assertThatThrownBy(() -> estoqueService.consumeReservation(1L, "caixa"))
                .isInstanceOf(StockReservationNotActiveException.class);
        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void releaseReservation_devolveSaldoESemGerarMovimento() {
        StockReservation reservation = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1");
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);
        when(stockReservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockReservation result = estoqueService.releaseReservation(1L, "caixa");

        assertThat(result.status()).isEqualTo(ReservationStatus.RELEASED);
        verify(stockBalanceRepository).save(argThat(b -> b.reservedQuantity().signum() == 0
                && b.quantity().compareTo(new BigDecimal("10.000")) == 0));
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void releaseReservation_throwsWhenReservaNaoExiste() {
        when(stockReservationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.releaseReservation(99L, "caixa"))
                .isInstanceOf(StockReservationNotFoundException.class);
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void releaseReservation_throwsWhenReservaJaNaoEstaAtiva() {
        StockReservation consumed = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1").consumed();
        when(stockReservationRepository.findById(1L)).thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> estoqueService.releaseReservation(1L, "caixa"))
                .isInstanceOf(StockReservationNotActiveException.class);
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void releaseReservationsByOwner_liberaTodasAsAtivasDoDonoERetornaQuantidade() {
        StockReservation r1 = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1");
        StockReservation r2 = activeReservation(2L, "CARV-001", 1L, "1.000", "CHECKOUT-1");
        StockBalance balance1 = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);
        StockBalance balance2 = StockBalance.of(11L, "CARV-001", 1L, new BigDecimal("5.000"),
                new BigDecimal("1.000"), 0L);
        when(stockReservationRepository.findActiveByOwnerReference("CHECKOUT-1")).thenReturn(List.of(r1, r2));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(balance1));
        when(stockBalanceRepository.findBySkuAndWarehouseId("CARV-001", 1L)).thenReturn(Optional.of(balance2));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int released = estoqueService.releaseReservationsByOwner("CHECKOUT-1", "caixa");

        assertThat(released).isEqualTo(2);
        verify(stockReservationRepository, times(2)).save(argThat(r -> r.status() == ReservationStatus.RELEASED));
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void releaseReservationsByOwner_semReservaAtiva_retornaZero() {
        when(stockReservationRepository.findActiveByOwnerReference("CHECKOUT-1")).thenReturn(List.of());

        assertThat(estoqueService.releaseReservationsByOwner("CHECKOUT-1", "caixa")).isZero();
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void consumeReservationsByOwner_consomeTodasAsAtivasDoDonoERetornaQuantidade() {
        StockReservation r1 = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1");
        StockReservation r2 = activeReservation(2L, "CARV-001", 1L, "1.000", "CHECKOUT-1");
        StockBalance balance1 = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);
        StockBalance balance2 = StockBalance.of(11L, "CARV-001", 1L, new BigDecimal("5.000"),
                new BigDecimal("1.000"), 0L);
        when(stockReservationRepository.findActiveByOwnerReference("CHECKOUT-1")).thenReturn(List.of(r1, r2));
        // consumeReservation() refaz a busca por id — mesma reserva já ativa, achada de novo.
        when(stockReservationRepository.findById(1L)).thenReturn(Optional.of(r1));
        when(stockReservationRepository.findById(2L)).thenReturn(Optional.of(r2));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(balance1));
        when(stockBalanceRepository.findBySkuAndWarehouseId("CARV-001", 1L)).thenReturn(Optional.of(balance2));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int consumed = estoqueService.consumeReservationsByOwner("CHECKOUT-1", "caixa");

        assertThat(consumed).isEqualTo(2);
        verify(stockReservationRepository, times(2)).save(argThat(r -> r.status() == ReservationStatus.CONSUMED));
        verify(stockMovementRepository, times(2)).save(argThat(m -> m.type() == MovementType.SAIDA));
    }

    @Test
    void consumeReservationsByOwner_semReservaAtiva_retornaZero() {
        when(stockReservationRepository.findActiveByOwnerReference("CHECKOUT-1")).thenReturn(List.of());

        assertThat(estoqueService.consumeReservationsByOwner("CHECKOUT-1", "caixa")).isZero();
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void getStockReservation_retornaQuandoEncontrada() {
        StockReservation reservation = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1");
        when(stockReservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThat(estoqueService.getStockReservation(1L)).isEqualTo(reservation);
    }

    @Test
    void getStockReservation_throwsWhenNaoEncontrada() {
        when(stockReservationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getStockReservation(99L))
                .isInstanceOf(StockReservationNotFoundException.class);
    }

    @Test
    void listReservations_comWarehouseCode_resolveParaOId() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockReservationRepository.findByFilters("NARG-001", 1L, ReservationStatus.ACTIVE, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        estoqueService.listReservations("NARG-001", "LOJA-01", ReservationStatus.ACTIVE, 0, 20);

        verify(stockReservationRepository).findByFilters("NARG-001", 1L, ReservationStatus.ACTIVE, 0, 20);
    }

    @Test
    void listReservations_semWarehouseCode_naoResolveDeposito() {
        when(stockReservationRepository.findByFilters(null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        estoqueService.listReservations(null, null, null, 0, 20);

        verify(stockReservationRepository).findByFilters(null, null, null, 0, 20);
        verify(warehouseRepository, never()).findByCode(any());
    }

    @Test
    void listReservations_throwsWhenWarehouseCodeInformadoNaoExiste() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.listReservations(null, "INEXISTENTE", null, 0, 20))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void expireReservations_devolveSaldoDeCadaVencidaEMarcaExpired() {
        StockReservation r1 = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1");
        StockReservation r2 = activeReservation(2L, "CARV-001", 1L, "1.000", "CHECKOUT-2");
        StockBalance balance1 = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"),
                new BigDecimal("2.000"), 0L);
        StockBalance balance2 = StockBalance.of(11L, "CARV-001", 1L, new BigDecimal("5.000"),
                new BigDecimal("1.000"), 0L);
        when(stockReservationRepository.findExpired(any(), eq(200))).thenReturn(List.of(r1, r2));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(balance1));
        when(stockBalanceRepository.findBySkuAndWarehouseId("CARV-001", 1L)).thenReturn(Optional.of(balance2));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int expired = estoqueService.expireReservations(200);

        assertThat(expired).isEqualTo(2);
        verify(stockBalanceRepository).save(argThat(b -> b.sku().equals("NARG-001") && b.reservedQuantity().signum() == 0));
        verify(stockBalanceRepository).save(argThat(b -> b.sku().equals("CARV-001") && b.reservedQuantity().signum() == 0));
        verify(stockReservationRepository, times(2)).save(argThat(r -> r.status() == ReservationStatus.EXPIRED));
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void expireReservations_semVencidas_retornaZeroSemEfeitoColateral() {
        when(stockReservationRepository.findExpired(any(), eq(200))).thenReturn(List.of());

        assertThat(estoqueService.expireReservations(200)).isZero();
        verify(stockBalanceRepository, never()).save(any());
        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void expireReservations_saldoAusente_naoFalhaEAindaAssimMarcaExpired() {
        StockReservation reservation = activeReservation(1L, "NARG-001", 1L, "2.000", "CHECKOUT-1");
        when(stockReservationRepository.findExpired(any(), eq(200))).thenReturn(List.of(reservation));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());
        when(stockReservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int expired = estoqueService.expireReservations(200);

        assertThat(expired).isEqualTo(1);
        verify(stockBalanceRepository, never()).save(any());
        verify(stockReservationRepository).save(argThat(r -> r.status() == ReservationStatus.EXPIRED));
    }

    // ── Alerta de vencimento de lote (EST-F008) ─────────────────────────────────────────────

    @Test
    void alertExpiringLots_notificaEMarcaAlertado() {
        StockLot lot = StockLot.of(1L, "ESS-001", 1L, "LOTE-A", LocalDate.of(2026, 8, 5),
                new BigDecimal("5.000"), null, 0L);
        when(stockLotRepository.findExpiringSoon(any(), eq(200))).thenReturn(List.of(lot));
        when(stockLotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findUsernamesByPermission("ESTOQUE_STOCK_MANAGE")).thenReturn(Set.of("gerente-estoque"));

        int alerted = estoqueService.alertExpiringLots(7, 200);

        assertThat(alerted).isEqualTo(1);
        verify(notificationUseCase).notify(eq("gerente-estoque"), eq(NotificationType.SYSTEM), any(), any());
        verify(stockLotRepository).save(argThat(l -> l.lotCode().equals("LOTE-A") && l.alertedAt() != null));
    }

    @Test
    void alertExpiringLots_semLotesVencendo_naoNotificaERetornaZero() {
        when(stockLotRepository.findExpiringSoon(any(), eq(200))).thenReturn(List.of());

        int alerted = estoqueService.alertExpiringLots(7, 200);

        assertThat(alerted).isZero();
        verify(notificationUseCase, never()).notify(any(), any(), any(), any());
        verify(stockLotRepository, never()).save(any());
    }

    // ── Atributos do próprio SKU pai ──────────────────────────────────────────

    @Test
    void createProduct_persisteOsAtributosDeRaiz() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.createProduct("ATR-001", "Essência", "essencia", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil"), new ProductAttribute("Peso", "50g")));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().attributes())
                .extracting(ProductAttribute::type, ProductAttribute::value)
                .containsExactly(tuple("Origem", "Brasil"), tuple("Peso", "50g"));
    }

    @Test
    void createProduct_semAtributos_nasceComListaVazia() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.createProduct("ATR-002", "Carvão", "carvao", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().attributes()).isEmpty();
    }

    @Test
    void updateProduct_substituiOConjuntoDeAtributos() {
        Product atual = Product.of(1L, "ATR-003", "Essência", "essencia", true, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil")));
        when(productRepository.findBySku("ATR-003")).thenReturn(Optional.of(atual));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.updateProduct("ATR-003", null, null, null, null, null, null, null, null, null, null,
                List.of(new ProductAttribute("Peso", "50g")));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().attributes())
                .extracting(ProductAttribute::type).containsExactly("Peso");
    }

    @Test
    void updateProduct_atributosNulos_preservamOsAtuais() {
        Product atual = Product.of(1L, "ATR-004", "Essência", "essencia", true, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil")));
        when(productRepository.findBySku("ATR-004")).thenReturn(Optional.of(atual));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Um PATCH que só renomeia não pode apagar os atributos.
        estoqueService.updateProduct("ATR-004", "Novo Nome", null, null, null, null, null, null, null, null, null,
                null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().attributes()).extracting(ProductAttribute::type).containsExactly("Origem");
        assertThat(captor.getValue().name()).isEqualTo("Novo Nome");
    }

    @Test
    void updateProduct_listaVaziaDeAtributos_limpaOConjunto() {
        // Mesma assimetria já adotada para images: nulo mantém, lista vazia apaga.
        Product atual = Product.of(1L, "ATR-005", "Essência", "essencia", true, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil")));
        when(productRepository.findBySku("ATR-005")).thenReturn(Optional.of(atual));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.updateProduct("ATR-005", null, null, null, null, null, null, null, null, null, null,
                List.of());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().attributes()).isEmpty();
    }

    @Test
    void updateProduct_atributosDeRaizNaoTocamNasVariacoes() {
        Product atual = Product.of(1L, "ATR-006", "Essência", "essencia", true,
                List.of(ProductVariant.of(9L, "ATR-006-A", List.of(new ProductAttribute("Sabor", "Menta")), true)),
                Pricing.empty(), ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of());
        when(productRepository.findBySku("ATR-006")).thenReturn(Optional.of(atual));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.updateProduct("ATR-006", null, null, null, null, null, null, null, null, null, null,
                List.of(new ProductAttribute("Origem", "Brasil")));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().variants().get(0).attributes())
                .extracting(ProductAttribute::type).containsExactly("Sabor");
        assertThat(captor.getValue().attributes())
                .extracting(ProductAttribute::type).containsExactly("Origem");
    }

    // ── Preço por variação: precedência no caminho do PDV (EST-F020) ─────────

    @Test
    void findPricingBySku_skuDeVariacaoComPrecoProprio_devolveODaVariacao() {
        Product pai = Product.of(1L, "VAR-001", "Essência", "essencia", true,
                List.of(ProductVariant.of(9L, "VAR-001-100G", List.of(), true,
                        Pricing.of(null, null, new BigDecimal("99.00")))),
                Pricing.of(null, null, new BigDecimal("30.00")));
        when(productRepository.findByAnySku("VAR-001-100G")).thenReturn(Optional.of(pai));

        assertThat(estoqueService.findPricingBySku("VAR-001-100G").salePrice())
                .isEqualByComparingTo("99.00");
    }

    @Test
    void findPricingBySku_skuDeVariacaoSemPrecoProprio_herdaODoPai() {
        Product pai = Product.of(1L, "VAR-002", "Essência", "essencia", true,
                List.of(ProductVariant.of(9L, "VAR-002-MENTA", List.of(), true)),
                Pricing.of(null, null, new BigDecimal("30.00")));
        when(productRepository.findByAnySku("VAR-002-MENTA")).thenReturn(Optional.of(pai));

        assertThat(estoqueService.findPricingBySku("VAR-002-MENTA").salePrice())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void findPricingBySku_skuDoPai_naoEAfetadoPeloPrecoDaVariacao() {
        Product pai = Product.of(1L, "VAR-003", "Essência", "essencia", true,
                List.of(ProductVariant.of(9L, "VAR-003-100G", List.of(), true,
                        Pricing.of(null, null, new BigDecimal("99.00")))),
                Pricing.of(null, null, new BigDecimal("30.00")));
        when(productRepository.findByAnySku("VAR-003")).thenReturn(Optional.of(pai));

        assertThat(estoqueService.findPricingBySku("VAR-003").salePrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void findPricingBySku_naoFazConsultaExtraParaResolverAVariacao() {
        // findByAnySku já traz as variações com JOIN FETCH; uma segunda consulta aqui seria N+1
        // no caminho mais quente do PDV.
        Product pai = Product.of(1L, "VAR-004", "Essência", "essencia", true,
                List.of(ProductVariant.of(9L, "VAR-004-A", List.of(), true,
                        Pricing.of(null, null, new BigDecimal("99.00")))),
                Pricing.of(null, null, new BigDecimal("30.00")));
        when(productRepository.findByAnySku("VAR-004-A")).thenReturn(Optional.of(pai));

        estoqueService.findPricingBySku("VAR-004-A");

        verify(productRepository, times(1)).findByAnySku("VAR-004-A");
        verify(productRepository, never()).findBySku(anyString());
    }

    @Test
    void createProduct_persisteOPrecoProprioDaVariacao() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.createProduct("VAR-005", "Essência", "essencia",
                List.of(ProductVariant.create("VAR-005-50G", List.of()),
                        ProductVariant.create("VAR-005-100G", List.of(),
                                Pricing.of(null, null, new BigDecimal("99.00")))),
                Pricing.of(null, null, new BigDecimal("30.00")), null, null, false, false, null, null, List.of());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().variants())
                .extracting(ProductVariant::sku, ProductVariant::hasOwnPricing)
                .containsExactly(tuple("VAR-005-50G", false), tuple("VAR-005-100G", true));
    }

    @Test
    void custoDoKit_usaOCustoProprioDaVariacaoQuandoOComponenteEUmaVariacao() {
        // A soma do kit precisa seguir a mesma precedência; ler o custo do pai daria um kit mais
        // barato do que realmente é.
        Product kit = Product.of(1L, "KIT-001", "Kit", "kits", true, List.of(),
                Pricing.of(null, null, new BigDecimal("150.00")), ProductType.KIT, false);
        Product componentePai = Product.of(2L, "COMP-001", "Essência", "essencia", true,
                List.of(ProductVariant.of(9L, "COMP-001-100G", List.of(), true,
                        Pricing.of(new BigDecimal("40.00"), null, null))),
                Pricing.of(new BigDecimal("10.00"), null, null));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kit));
        when(productRepository.findByAnySku("COMP-001-100G")).thenReturn(Optional.of(componentePai));
        when(kitComponentRepository.findByKitSku("KIT-001"))
                .thenReturn(List.of(KitComponent.of(1L, "KIT-001", "COMP-001-100G", BigDecimal.ONE)));

        assertThat(estoqueService.findPricingBySku("KIT-001").costPrice()).isEqualByComparingTo("40.00");
    }


    // ── Categorias do catálogo ───────────────────────────────────────────────

    @Test
    void createCategory_recusaNomeJaExistente() {
        when(categoryRepository.findByName("Narguilé"))
                .thenReturn(Optional.of(Category.of(1L, "Narguilé", false, 0, true)));

        assertThatThrownBy(() -> estoqueService.createCategory("Narguilé", false, 0))
                .isInstanceOf(DuplicateCategoryNameException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_renomear_propagaONomeParaOsProdutosVinculados() {
        // Sem isso a vitrine exibiria o rótulo antigo e ordenaria pelo novo.
        Category atual = Category.of(1L, "Narguilé", false, 0, true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(atual));
        when(categoryRepository.findByName("Narguilés")).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.updateCategory(1L, "Narguilés", null, null);

        verify(productRepository).renameCategory(1L, "Narguilés");
    }

    @Test
    void updateCategory_semRenomear_naoTocaNosProdutos() {
        Category atual = Category.of(1L, "Narguilé", false, 0, true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(atual));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.updateCategory(1L, null, true, 5);

        verify(productRepository, never()).renameCategory(anyLong(), anyString());
    }

    @Test
    void updateCategory_renomearParaNomeDeOutraCategoria_e409() {
        // Deixar passar daria erro de constraint no flush, longe daqui e sem o errorCode do contrato.
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(Category.of(1L, "Narguilé", false, 0, true)));
        when(categoryRepository.findByName("Essência"))
                .thenReturn(Optional.of(Category.of(2L, "Essência", false, 0, true)));

        assertThatThrownBy(() -> estoqueService.updateCategory(1L, "Essência", null, null))
                .isInstanceOf(DuplicateCategoryNameException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_renomearMantendoOProprioNome_naoColideConsigoMesma() {
        Category atual = Category.of(1L, "Narguilé", false, 0, true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(atual));
        when(categoryRepository.findByName("Narguilé")).thenReturn(Optional.of(atual));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> estoqueService.updateCategory(1L, "Narguilé", true, null))
                .doesNotThrowAnyException();
    }

    @Test
    void updateCategory_idInexistente_e404() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.updateCategory(99L, "X", null, null))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void setCategoryActive_naoMexeNosProdutos() {
        // Categoria é organização de vitrine, não permissão de venda.
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(Category.of(1L, "Narguilé", false, 0, true)));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Category desativada = estoqueService.setCategoryActive(1L, false);

        assertThat(desativada.active()).isFalse();
        verifyNoInteractions(productRepository);
    }

    @Test
    void deleteCategory_throwsWhenProductsLinked() {
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(Category.of(1L, "Narguilé", false, 0, true)));
        when(productRepository.countByCategoryId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> estoqueService.deleteCategory(1L))
                .isInstanceOf(CategoryHasProductsException.class);
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteCategory_succeedsWhenEmpty() {
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(Category.of(1L, "Narguilé", false, 0, true)));
        when(productRepository.countByCategoryId(1L)).thenReturn(0L);

        estoqueService.deleteCategory(1L);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteCategory_throwsWhenIdNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.deleteCategory(99L))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void listBrands_delegatesToRepository() {
        PageResult<com.cernecommerce.core.domain.model.estoque.Brand> page = new PageResult<>(
                List.of(com.cernecommerce.core.domain.model.estoque.Brand.of(1L, "Mahal", true)),
                0, 20, 1L, 1);
        when(brandRepository.findByNameContaining("mahal", 0, 20)).thenReturn(page);

        var result = estoqueService.listBrands("mahal", 0, 20);

        assertThat(result.content()).extracting("name").containsExactly("Mahal");
    }

    @Test
    void listBrands_semSearch_listaTodasPaginado() {
        PageResult<Brand> page = new PageResult<>(List.of(Brand.of(1L, "Zomo", true)), 0, 20, 1L, 1);
        when(brandRepository.findAll(0, 20)).thenReturn(page);

        var result = estoqueService.listBrands(null, 0, 20);

        assertThat(result.content()).extracting("name").containsExactly("Zomo");
        verify(brandRepository, never()).findByNameContaining(any(), anyInt(), anyInt());
    }

    // ── Marcas do catálogo ────────────────────────────────────────────────────

    @Test
    void createBrand_recusaNomeJaExistente() {
        when(brandRepository.findByName("Zomo")).thenReturn(Optional.of(Brand.of(1L, "Zomo", true)));

        assertThatThrownBy(() -> estoqueService.createBrand("Zomo"))
                .isInstanceOf(DuplicateBrandNameException.class);
        verify(brandRepository, never()).save(any());
    }

    @Test
    void updateBrand_renomear_propagaONomeParaOsProdutosVinculados() {
        Brand atual = Brand.of(1L, "Zomo", true);
        when(brandRepository.findById(1L)).thenReturn(Optional.of(atual));
        when(brandRepository.findByName("Zomo Distribuidora")).thenReturn(Optional.empty());
        when(brandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.updateBrand(1L, "Zomo Distribuidora");

        verify(productRepository).renameBrand(1L, "Zomo Distribuidora");
    }

    @Test
    void updateBrand_renomearParaNomeDeOutraMarca_e409() {
        when(brandRepository.findById(1L)).thenReturn(Optional.of(Brand.of(1L, "Zomo", true)));
        when(brandRepository.findByName("Alfaraby")).thenReturn(Optional.of(Brand.of(2L, "Alfaraby", true)));

        assertThatThrownBy(() -> estoqueService.updateBrand(1L, "Alfaraby"))
                .isInstanceOf(DuplicateBrandNameException.class);
        verify(brandRepository, never()).save(any());
    }

    @Test
    void updateBrand_idInexistente_e404() {
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.updateBrand(99L, "X"))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void setBrandActive_naoMexeNosProdutos() {
        when(brandRepository.findById(1L)).thenReturn(Optional.of(Brand.of(1L, "Zomo", true)));
        when(brandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Brand desativada = estoqueService.setBrandActive(1L, false);

        assertThat(desativada.active()).isFalse();
        verifyNoInteractions(productRepository);
    }

    @Test
    void deleteBrand_throwsWhenProductsLinked() {
        when(brandRepository.findById(1L)).thenReturn(Optional.of(Brand.of(1L, "Zomo", true)));
        when(productRepository.countByBrandId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> estoqueService.deleteBrand(1L))
                .isInstanceOf(BrandHasProductsException.class);
        verify(brandRepository, never()).deleteById(any());
    }

    @Test
    void deleteBrand_succeedsWhenEmpty() {
        when(brandRepository.findById(1L)).thenReturn(Optional.of(Brand.of(1L, "Zomo", true)));
        when(productRepository.countByBrandId(1L)).thenReturn(0L);

        estoqueService.deleteBrand(1L);

        verify(brandRepository).deleteById(1L);
    }

    @Test
    void deleteBrand_throwsWhenIdNotFound() {
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.deleteBrand(99L))
                .isInstanceOf(BrandNotFoundException.class);
        verify(brandRepository, never()).deleteById(any());
    }

    @Test
    void createProduct_comTextoDeMarcaConhecido_reencontraAExistente() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(brandRepository.findByName("Zomo")).thenReturn(Optional.of(Brand.of(7L, "Zomo", true)));

        Product created = estoqueService.createProduct("ESS-001", "Essência", "essencia", List.of(), Pricing.empty(),
                "Zomo", null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, null, null, null);

        assertThat(created.brandId()).isEqualTo(7L);
        verify(brandRepository, never()).save(any());
    }

    @Test
    void createProduct_comTextoDeMarcaDesconhecido_criaNova() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(brandRepository.findByName("Nova Marca")).thenReturn(Optional.empty());
        when(brandRepository.save(any())).thenAnswer(inv -> Brand.of(10L, "Nova Marca", true));

        Product created = estoqueService.createProduct("ESS-002", "Essência", "essencia", List.of(), Pricing.empty(),
                "Nova Marca", null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, null, null, null);

        assertThat(created.brandId()).isEqualTo(10L);
        verify(brandRepository).save(argThat(b -> b.name().equals("Nova Marca")));
    }

    @Test
    void createProduct_comBrandIdInexistente_e404() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.createProduct("ESS-003", "Essência", "essencia", List.of(),
                Pricing.empty(), null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, null, 99L, null))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void updateProduct_comBrandId_vinculaEPropagaONomeDenormalizado() {
        Product current = Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of());
        when(productRepository.findBySku("ESS-001")).thenReturn(Optional.of(current));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(brandRepository.findById(7L)).thenReturn(Optional.of(Brand.of(7L, "Zomo", true)));

        Product updated = estoqueService.updateProduct("ESS-001", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, 7L, null);

        assertThat(updated.brandId()).isEqualTo(7L);
        assertThat(updated.brand()).isEqualTo("Zomo");
    }

    // ── Margem média por categoria/marca (item 7) ─────────────────────────────

    @Test
    void averageMarginPercentByCategoryIds_calculaMediaSimplesIgnorandoSemMargem() {
        Product comMargem60 = Product.of(1L, "P1", "Prod 1", "cat", true, List.of(),
                Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("100.00")));
        Product comMargem20 = Product.of(2L, "P2", "Prod 2", "cat", true, List.of(),
                Pricing.of(new BigDecimal("80.00"), null, new BigDecimal("100.00")));
        Product semPreco = Product.of(3L, "P3", "Prod 3", "cat", true, List.of());
        when(productRepository.findAllByCategoryId(1L)).thenReturn(List.of(comMargem60, comMargem20, semPreco));

        Map<Long, BigDecimal> result = estoqueService.averageMarginPercentByCategoryIds(List.of(1L));

        assertThat(result.get(1L)).isEqualByComparingTo("40.00");
    }

    @Test
    void averageMarginPercentByCategoryIds_semNenhumProdutoComMargem_retornaNulo() {
        when(productRepository.findAllByCategoryId(1L)).thenReturn(List.of(Product.of(1L, "P1", "Prod 1", "cat",
                true, List.of())));

        Map<Long, BigDecimal> result = estoqueService.averageMarginPercentByCategoryIds(List.of(1L));

        assertThat(result.get(1L)).isNull();
    }

    @Test
    void averageMarginPercentByBrandIds_delegatesToRepository() {
        Product comMargem = Product.of(1L, "P1", "Prod 1", "cat", true, List.of(),
                Pricing.of(new BigDecimal("50.00"), null, new BigDecimal("100.00")));
        when(productRepository.findAllByBrandId(7L)).thenReturn(List.of(comMargem));

        Map<Long, BigDecimal> result = estoqueService.averageMarginPercentByBrandIds(List.of(7L));

        assertThat(result.get(7L)).isEqualByComparingTo("50.00");
    }

    // ── Vocabulário de atributos (item 5) ──────────────────────────────────────

    @Test
    void createAttributeType_recusaNomeJaExistente() {
        when(attributeTypeRepository.findByName("Sabor"))
                .thenReturn(Optional.of(AttributeType.of(1L, "Sabor")));

        assertThatThrownBy(() -> estoqueService.createAttributeType("Sabor"))
                .isInstanceOf(DuplicateAttributeTypeNameException.class);
        verify(attributeTypeRepository, never()).save(any());
    }

    @Test
    void createAttributeType_criaQuandoNomeNovo() {
        when(attributeTypeRepository.findByName("Intensidade")).thenReturn(Optional.empty());
        when(attributeTypeRepository.save(any())).thenAnswer(inv -> AttributeType.of(5L, "Intensidade"));

        AttributeType created = estoqueService.createAttributeType("Intensidade");

        assertThat(created.name()).isEqualTo("Intensidade");
    }

    @Test
    void listAttributeTypes_delegatesToRepository() {
        when(attributeTypeRepository.findAllOrderByName())
                .thenReturn(List.of(AttributeType.of(1L, "Aroma"), AttributeType.of(2L, "Sabor")));

        List<AttributeType> result = estoqueService.listAttributeTypes();

        assertThat(result).extracting(AttributeType::name).containsExactly("Aroma", "Sabor");
    }

    // ── DELETE reorder-point (item 4) ─────────────────────────────────────────

    @Test
    void deleteReorderPoint_delegatesToRepository() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));

        estoqueService.deleteReorderPoint("NARG-001", "LOJA-01");

        verify(reorderPointRepository).deleteBySkuAndWarehouseId("NARG-001", 1L);
    }

    @Test
    void deleteReorderPoint_idempotente_naoChecaExistenciaAntes() {
        // Item 4: não é erro remover o que não existe — sem checagem de existência prévia.
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));

        assertThatCode(() -> estoqueService.deleteReorderPoint("SEM-MINIMO", "LOJA-01"))
                .doesNotThrowAnyException();
        verify(reorderPointRepository, never()).findBySkuAndWarehouseId(any(), any());
    }

    @Test
    void deleteReorderPoint_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.deleteReorderPoint("NARG-001", "INEXISTENTE"))
                .isInstanceOf(WarehouseNotFoundException.class);
        verify(reorderPointRepository, never()).deleteBySkuAndWarehouseId(any(), any());
    }

    // ── Histórico de compras por SKU (item 2) ─────────────────────────────────

    @Test
    void listPurchaseHistory_delegatesToRepository() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockMovement entrada = StockMovement.of(1L, "NARG-001", 1L, MovementType.ENTRADA, new BigDecimal("24.000"),
                "Recebimento de mercadoria - fornecedor #3", "gerente", Instant.parse("2026-07-01T10:00:00Z"),
                null, new BigDecimal("42.50"), 87L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockMovementRepository.findEntradasBySkuAndWarehouseId("NARG-001", 1L, 0, 20))
                .thenReturn(new PageResult<>(List.of(entrada), 0, 20, 1L, 1));

        PageResult<StockMovement> result = estoqueService.listPurchaseHistory("NARG-001", "LOJA-01", 0, 20);

        assertThat(result.content()).containsExactly(entrada);
        assertThat(result.content().get(0).goodsReceiptId()).isEqualTo(87L);
    }

    @Test
    void listPurchaseHistory_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.listPurchaseHistory("NARG-001", "INEXISTENTE", 0, 20))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    // ── DELETE de variante (item 8) ───────────────────────────────────────────

    @Test
    void deleteVariant_removeQuandoSemHistoricoDeEstoque() {
        ProductVariant variante = ProductVariant.create("ESS-001-50G", List.of(), null, null);
        Product current = Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of(variante));
        when(productRepository.findBySku("ESS-001")).thenReturn(Optional.of(current));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockBalanceRepository.existsBySku("ESS-001-50G")).thenReturn(false);
        when(stockMovementRepository.existsBySku("ESS-001-50G")).thenReturn(false);

        Product updated = estoqueService.deleteVariant("ESS-001", "ESS-001-50G");

        assertThat(updated.variants()).isEmpty();
    }

    @Test
    void deleteVariant_recusaQuandoHaSaldoDeEstoque() {
        ProductVariant variante = ProductVariant.create("ESS-001-50G", List.of(), null, null);
        Product current = Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of(variante));
        when(productRepository.findBySku("ESS-001")).thenReturn(Optional.of(current));
        when(stockBalanceRepository.existsBySku("ESS-001-50G")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.deleteVariant("ESS-001", "ESS-001-50G"))
                .isInstanceOf(VariantHasStockHistoryException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteVariant_recusaQuandoHaMovimentacaoDeEstoque() {
        ProductVariant variante = ProductVariant.create("ESS-001-50G", List.of(), null, null);
        Product current = Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of(variante));
        when(productRepository.findBySku("ESS-001")).thenReturn(Optional.of(current));
        when(stockBalanceRepository.existsBySku("ESS-001-50G")).thenReturn(false);
        when(stockMovementRepository.existsBySku("ESS-001-50G")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.deleteVariant("ESS-001", "ESS-001-50G"))
                .isInstanceOf(VariantHasStockHistoryException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteVariant_throwsWhenVariantNotFound() {
        Product current = Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of());
        when(productRepository.findBySku("ESS-001")).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> estoqueService.deleteVariant("ESS-001", "ESS-001-INEXISTENTE"))
                .isInstanceOf(ProductVariantNotFoundException.class);
    }

    // ── Filtros de GET /estoque/movements (item 6) ────────────────────────────

    @Test
    void listMovements_comFiltroDeTipoEData_repassaAoRepositorio() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-31T23:59:59Z");
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockMovementRepository.findBySkuAndWarehouseId("NARG-001", 1L, MovementType.ENTRADA, from, to, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        estoqueService.listMovements("NARG-001", "LOJA-01", MovementType.ENTRADA, from, to, 0, 20);

        verify(stockMovementRepository).findBySkuAndWarehouseId("NARG-001", 1L, MovementType.ENTRADA, from, to, 0,
                20);
    }

    // ── Lista de Reposição (item 1) ───────────────────────────────────────────

    @Test
    void upsertReplenishmentItem_tiraSnapshotDeProdutoSaldoPontoDeReposicaoECustoEUltimaCompra() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        Product product = Product.of(1L, "ESS-001", "Essência Babylon 50g", "essencias", true, List.of(),
                Pricing.of(new BigDecimal("45.00"), null, new BigDecimal("90.00")), ProductType.SIMPLES, false,
                "Zomo");
        StockBalance balance = StockBalance.of(1L, "ESS-001", 1L, new BigDecimal("2.000"), 0L);
        ReorderPoint reorderPoint = ReorderPoint.of(1L, "ESS-001", 1L, new BigDecimal("10.000"));
        StockMovement ultimaCompra = StockMovement.of(9L, "ESS-001", 1L, MovementType.ENTRADA,
                new BigDecimal("24.000"), "Recebimento de mercadoria - fornecedor #3", "gerente",
                Instant.parse("2026-07-01T10:00:00Z"), null, new BigDecimal("42.50"), 87L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(product));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.of(balance));
        when(reorderPointRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.of(reorderPoint));
        when(stockMovementRepository.findEntradasBySkuAndWarehouseId("ESS-001", 1L, 0, 1))
                .thenReturn(new PageResult<>(List.of(ultimaCompra), 0, 1, 1L, 1));
        when(replenishmentListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReplenishmentListItem result = estoqueService.upsertReplenishmentItem("ESS-001", "LOJA-01",
                new BigDecimal("12"), "pedir junto com o pedido da Zomo", "jeff");

        assertThat(result.productNameSnapshot()).isEqualTo("Essência Babylon 50g");
        assertThat(result.categorySnapshot()).isEqualTo("essencias");
        assertThat(result.brandSnapshot()).isEqualTo("Zomo");
        assertThat(result.currentStockSnapshot()).isEqualByComparingTo("2.000");
        assertThat(result.minStockSnapshot()).isEqualByComparingTo("10.000");
        // max(0, minStock - currentStock) = max(0, 10 - 2) = 8
        assertThat(result.suggestedQuantitySnapshot()).isEqualByComparingTo("8.000");
        assertThat(result.quantity()).isEqualByComparingTo("12");
        assertThat(result.unitCostSnapshot()).isEqualByComparingTo("45.00");
        assertThat(result.previousPurchaseQuantitySnapshot()).isEqualByComparingTo("24.000");
        assertThat(result.previousPurchaseUnitCostSnapshot()).isEqualByComparingTo("42.50");
        assertThat(result.previousPurchasedAtSnapshot()).isEqualTo(Instant.parse("2026-07-01T10:00:00Z"));
        assertThat(result.note()).isEqualTo("pedir junto com o pedido da Zomo");
        assertThat(result.createdBy()).isEqualTo("jeff");
    }

    @Test
    void upsertReplenishmentItem_semPontoDeReposicaoConfigurado_suggestedQuantityFicaNulo() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        Product product = Product.of(1L, "ESS-001", "Essência", "essencias", true, List.of());
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(product));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.empty());
        when(reorderPointRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.empty());
        when(stockMovementRepository.findEntradasBySkuAndWarehouseId("ESS-001", 1L, 0, 1))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 0L, 0));
        when(replenishmentListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReplenishmentListItem result = estoqueService.upsertReplenishmentItem("ESS-001", "LOJA-01",
                new BigDecimal("5"), null, "jeff");

        assertThat(result.minStockSnapshot()).isNull();
        assertThat(result.suggestedQuantitySnapshot()).isNull();
        assertThat(result.currentStockSnapshot()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.previousPurchasedAtSnapshot()).isNull();
    }

    @Test
    void upsertReplenishmentItem_throwsWhenProductNotFound() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.upsertReplenishmentItem("SKU-FANTASMA", "LOJA-01",
                BigDecimal.ONE, null, "jeff"))
                .isInstanceOf(ProductNotFoundException.class);
        verify(replenishmentListRepository, never()).save(any());
    }

    @Test
    void updateReplenishmentItem_alteraQuantityENote_semTocarSnapshot() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        ReplenishmentListItem current = ReplenishmentListItem.of(1L, "ESS-001", 1L, "Essência", "essencias", "Zomo",
                null, new BigDecimal("2.000"), new BigDecimal("10.000"), new BigDecimal("8.000"),
                new BigDecimal("12"), new BigDecimal("45.00"), null, null, null, "nota antiga",
                Instant.parse("2026-08-19T14:00:00Z"), "jeff");
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(replenishmentListRepository.findBySkuAndWarehouseId("ESS-001", 1L)).thenReturn(Optional.of(current));
        when(replenishmentListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReplenishmentListItem updated = estoqueService.updateReplenishmentItem("ESS-001", "LOJA-01",
                new BigDecimal("20"), "nota nova");

        assertThat(updated.quantity()).isEqualByComparingTo("20");
        assertThat(updated.note()).isEqualTo("nota nova");
        assertThat(updated.productNameSnapshot()).isEqualTo("Essência");
        assertThat(updated.minStockSnapshot()).isEqualByComparingTo("10.000");
    }

    @Test
    void updateReplenishmentItem_throwsWhenItemNotFound() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(replenishmentListRepository.findBySkuAndWarehouseId("SEM-ITEM", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.updateReplenishmentItem("SEM-ITEM", "LOJA-01",
                BigDecimal.ONE, null))
                .isInstanceOf(ReplenishmentItemNotFoundException.class);
    }

    @Test
    void deleteReplenishmentItem_delegatesToRepository() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));

        estoqueService.deleteReplenishmentItem("ESS-001", "LOJA-01");

        verify(replenishmentListRepository).deleteBySkuAndWarehouseId("ESS-001", 1L);
    }

    @Test
    void clearReplenishmentList_delegatesToRepository() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));

        estoqueService.clearReplenishmentList("LOJA-01");

        verify(replenishmentListRepository).deleteByWarehouseId(1L);
    }

    @Test
    void listReplenishmentItems_delegatesToRepository() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        ReplenishmentListItem item = ReplenishmentListItem.of(1L, "ESS-001", 1L, "Essência", "essencias", "Zomo",
                null, new BigDecimal("2.000"), new BigDecimal("10.000"), new BigDecimal("8.000"),
                new BigDecimal("12"), new BigDecimal("45.00"), null, null, null, null,
                Instant.parse("2026-08-19T14:00:00Z"), "jeff");
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(replenishmentListRepository.findByWarehouseId(1L)).thenReturn(List.of(item));

        List<ReplenishmentListItem> result = estoqueService.listReplenishmentItems("LOJA-01");

        assertThat(result).containsExactly(item);
    }

    @Test
    void countProductsByCategoryIds_delegatesToRepository() {
        when(productRepository.countProductsByCategoryIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 5L));

        Map<Long, Long> result = estoqueService.countProductsByCategoryIds(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 5L);
    }

    // ── Resolução de categoria no cadastro de produto (compatibilidade) ───────

    @Test
    void createProduct_comTextoDeCategoriaConhecido_reencontraAExistente() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findByName("narguile"))
                .thenReturn(Optional.of(Category.of(7L, "Narguilé", false, 0, true)));

        estoqueService.createProduct("CAT-001", "Produto", "narguile", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().categoryId()).isEqualTo(7L);
        // O texto passa a ser a grafia canônica da categoria, não a digitada.
        assertThat(captor.getValue().category()).isEqualTo("Narguilé");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createProduct_comTextoDeCategoriaNovo_criaACategoria() {
        // É o fluxo "Nova categoria..." do formulário: recusar quebraria o admin atual.
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findByName("Vaporizadores")).thenReturn(Optional.empty());
        when(categoryRepository.save(any()))
                .thenReturn(Category.of(9L, "Vaporizadores", false, 0, true));

        estoqueService.createProduct("CAT-002", "Produto", "Vaporizadores", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().categoryId()).isEqualTo(9L);
        verify(categoryRepository).save(any());
    }

    @Test
    void createProduct_comCategoryId_venceSobreOTextoEResolveONome() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findById(7L))
                .thenReturn(Optional.of(Category.of(7L, "Narguilé", false, 0, true)));

        estoqueService.createProduct("CAT-003", "Produto", "texto-ignorado", List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), 7L);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().categoryId()).isEqualTo(7L);
        assertThat(captor.getValue().category()).isEqualTo("Narguilé");
        verify(categoryRepository, never()).findByName(anyString());
    }

    @Test
    void createProduct_comCategoryIdInexistente_e404() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.createProduct("CAT-004", "Produto", null, List.of(),
                Pricing.empty(), null, null, false, false, null, null, List.of(), List.of(), 99L))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_semCategoriaNenhuma_ficaSemVinculo() {
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.createProduct("CAT-005", "Produto", null, List.of(), Pricing.empty(),
                null, null, false, false, null, null, List.of(), List.of(), null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().categoryId()).isNull();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void updateProduct_semCategoria_mantemOVinculoAtual() {
        Product atual = Product.of(1L, "CAT-006", "Produto", "Narguilé", true, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(), 7L);
        when(productRepository.findBySku("CAT-006")).thenReturn(Optional.of(atual));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.updateProduct("CAT-006", "Novo Nome", null, null, null, null, null, null, null, null,
                null, null, null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().categoryId()).isEqualTo(7L);
        assertThat(captor.getValue().category()).isEqualTo("Narguilé");
    }

    // ── Mutação da grade de variantes pós-criação (EST-F024) ──────────────────

    @Test
    void addVariants_anexaSemTocarNasExistentes() {
        ProductVariant existente = ProductVariant.of(1L, "GRADE-001-A", List.of(), true);
        Product current = Product.of(10L, "GRADE-001", "Essência", "essencia", true, List.of(existente));
        when(productRepository.findBySku("GRADE-001")).thenReturn(Optional.of(current));
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ProductVariant> novas = List.of(ProductVariant.create("GRADE-001-B", List.of()));
        Product result = estoqueService.addVariants("GRADE-001", novas);

        assertThat(result.variants()).extracting(ProductVariant::sku)
                .containsExactlyInAnyOrder("GRADE-001-A", "GRADE-001-B");
        // A existente preserva o id — sem isso, ProductRepositoryImpl.save() (rebuild completo da
        // coleção) apagaria e recriaria a linha, quebrando o histórico de estoque que a referencia.
        assertThat(result.variants()).filteredOn(v -> v.sku().equals("GRADE-001-A"))
                .singleElement().extracting(ProductVariant::id).isEqualTo(1L);
    }

    @Test
    void addVariants_throwsWhenProductNotFound() {
        when(productRepository.findBySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.addVariants("SKU-FANTASMA",
                List.of(ProductVariant.create("SKU-FANTASMA-A", List.of()))))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void addVariants_throwsWhenProductIsKit() {
        Product kit = Product.of(1L, "KIT-001", "Kit", "combo", true, List.of(), Pricing.empty(), ProductType.KIT);
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(kit));

        assertThatThrownBy(() -> estoqueService.addVariants("KIT-001",
                List.of(ProductVariant.create("KIT-001-A", List.of()))))
                .isInstanceOf(KitHasVariantsException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void addVariants_throwsWhenNewSkuAlreadyExists() {
        Product current = Product.of(10L, "GRADE-002", "Essência", "essencia", true, List.of());
        when(productRepository.findBySku("GRADE-002")).thenReturn(Optional.of(current));
        when(productRepository.existsBySku("GRADE-002-X")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.addVariants("GRADE-002",
                List.of(ProductVariant.create("GRADE-002-X", List.of()))))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void addVariants_throwsWhenNewBarcodeAlreadyExists() {
        Product current = Product.of(10L, "GRADE-003", "Essência", "essencia", true, List.of());
        when(productRepository.findBySku("GRADE-003")).thenReturn(Optional.of(current));
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.existsByBarcode("7891234567895")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.addVariants("GRADE-003",
                List.of(ProductVariant.create("GRADE-003-A", List.of(), null, "7891234567895"))))
                .isInstanceOf(DuplicateBarcodeException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateVariant_aplicaPatchPreservandoAsDemaisVariacoes() {
        ProductVariant alvo = ProductVariant.of(1L, "GRADE-004-A", List.of(new ProductAttribute("sabor", "menta")),
                true);
        ProductVariant outra = ProductVariant.of(2L, "GRADE-004-B", List.of(), true);
        Product current = Product.of(10L, "GRADE-004", "Essência", "essencia", true, List.of(alvo, outra));
        when(productRepository.findBySku("GRADE-004")).thenReturn(Optional.of(current));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ProductAttribute> novosAtributos = List.of(new ProductAttribute("sabor", "uva"));
        Product result = estoqueService.updateVariant("GRADE-004", "GRADE-004-A", false, novosAtributos, null, null);

        ProductVariant atualizada = result.variants().stream()
                .filter(v -> v.sku().equals("GRADE-004-A")).findFirst().orElseThrow();
        assertThat(atualizada.active()).isFalse();
        assertThat(atualizada.attributes()).containsExactly(new ProductAttribute("sabor", "uva"));
        assertThat(atualizada.id()).as("preserva o id da linha existente").isEqualTo(1L);

        ProductVariant intocada = result.variants().stream()
                .filter(v -> v.sku().equals("GRADE-004-B")).findFirst().orElseThrow();
        assertThat(intocada).isEqualTo(outra);
    }

    @Test
    void updateVariant_camposNulosMantemOsAtuais() {
        Pricing pricingAtual = Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("99.90"));
        ProductVariant alvo = ProductVariant.of(1L, "GRADE-005-A", List.of(), true, pricingAtual, "7891111111116");
        Product current = Product.of(10L, "GRADE-005", "Essência", "essencia", true, List.of(alvo));
        when(productRepository.findBySku("GRADE-005")).thenReturn(Optional.of(current));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.updateVariant("GRADE-005", "GRADE-005-A", null, null, null, null);

        ProductVariant unchanged = result.variants().get(0);
        assertThat(unchanged.active()).isTrue();
        assertThat(unchanged.barcode()).isEqualTo("7891111111116");
        assertThat(unchanged.pricing().salePrice()).isEqualByComparingTo("99.90");
    }

    @Test
    void updateVariant_pricingUsaWithPatchSemApagarCamposJaCadastrados() {
        Pricing pricingAtual = Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("99.90"));
        ProductVariant alvo = ProductVariant.of(1L, "GRADE-006-A", List.of(), true, pricingAtual);
        Product current = Product.of(10L, "GRADE-006", "Essência", "essencia", true, List.of(alvo));
        when(productRepository.findBySku("GRADE-006")).thenReturn(Optional.of(current));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Manda só markupPercent — costPrice e salePrice já cadastrados não podem sumir.
        Pricing patch = Pricing.byMarkup(null, new BigDecimal("50.0000"));
        Product result = estoqueService.updateVariant("GRADE-006", "GRADE-006-A", null, null, patch, null);

        Pricing updated = result.variants().get(0).pricing();
        assertThat(updated.costPrice()).isEqualByComparingTo("40.00");
        assertThat(updated.salePrice()).isEqualByComparingTo("99.90");
        assertThat(updated.markupPercent()).isEqualByComparingTo("50.0000");
    }

    @Test
    void updateVariant_throwsWhenProductNotFound() {
        when(productRepository.findBySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.updateVariant("SKU-FANTASMA", "SKU-FANTASMA-A", true, null, null, null))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateVariant_throwsWhenVariantNotFound() {
        Product current = Product.of(10L, "GRADE-007", "Essência", "essencia", true, List.of());
        when(productRepository.findBySku("GRADE-007")).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> estoqueService.updateVariant("GRADE-007", "GRADE-007-FANTASMA", true, null, null, null))
                .isInstanceOf(ProductVariantNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateVariant_throwsWhenNewBarcodeAlreadyExists() {
        ProductVariant alvo = ProductVariant.of(1L, "GRADE-008-A", List.of(), true);
        Product current = Product.of(10L, "GRADE-008", "Essência", "essencia", true, List.of(alvo));
        when(productRepository.findBySku("GRADE-008")).thenReturn(Optional.of(current));
        when(productRepository.existsByBarcode("7891234567895")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.updateVariant("GRADE-008", "GRADE-008-A", null, null, null,
                "7891234567895"))
                .isInstanceOf(DuplicateBarcodeException.class);
        verify(productRepository, never()).save(any());
    }
    // ── Campos de mesa e de sessão do produto (PDV-F010) ─────────────────────────────────────

    @Test
    void createProduct_semTableSession_resolveOsDefaultsDaMigration() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.createProduct("MESA-001", "Produto", "categoria", List.of(),
                Pricing.empty(), null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, null, null, null);

        // "Omitido" não pode virar false: todo produto já cadastrado sai na mesa, e nenhum é de
        // sessão — mesma convenção de visibleInPos.
        assertThat(result.availableForTable()).isTrue();
        assertThat(result.sessionProduct()).isFalse();
        assertThat(result.sessionsPerUnit()).isNull();
        assertThat(result.openRoshPrice()).isNull();
    }

    @Test
    void createProduct_comTableSession_persisteOsQuatroCampos() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.createProduct("SESS-001", "Sessão de narguilé", "sessao", List.of(),
                Pricing.empty(), null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, false, null, null, null, null, null, null, null, null,
                new TableSessionCommand(false, true, 10, new BigDecimal("60.00")));

        assertThat(result.availableForTable()).isFalse();
        assertThat(result.sessionProduct()).isTrue();
        assertThat(result.sessionsPerUnit()).isEqualTo(10);
        assertThat(result.openRoshPrice()).isEqualByComparingTo("60.00");
    }

    @Test
    void updateProduct_tableSessionComCampoNulo_mantemOValorAtual() {
        Product atual = Product.of(1L, "SESS-001", "Sessão", "sessao", true, List.of())
                .withSessionProduct(true)
                .withSessionsPerUnit(10)
                .withOpenRoshPrice(new BigDecimal("60.00"));
        when(productRepository.findBySku("SESS-001")).thenReturn(Optional.of(atual));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Só o openRoshPrice vem preenchido: os outros três são "não mexa", semântica de PATCH.
        Product result = estoqueService.updateProduct("SESS-001", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                new TableSessionCommand(null, null, null, new BigDecimal("75.00")));

        assertThat(result.openRoshPrice()).isEqualByComparingTo("75.00");
        assertThat(result.sessionProduct()).isTrue();
        assertThat(result.sessionsPerUnit()).isEqualTo(10);
    }

    /**
     * O SKU da variação serve para saber qual essência sai do estoque; disponibilidade na mesa e
     * preço de open rosh não têm versão por sabor, e vêm sempre do produto pai.
     */
    @Test
    void resolveSaleInfo_devolveOsCamposDeMesaDoProdutoPai() {
        Product pai = Product.of(1L, "SESS-001", "Sessão", "sessao", true, List.of())
                .withPricing(Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")))
                .withAvailableForTable(true)
                .withSessionProduct(true)
                .withOpenRoshPrice(new BigDecimal("60.00"));
        when(productRepository.findByAnySku("SESS-BLUE")).thenReturn(Optional.of(pai));

        CatalogSaleInfo info = estoqueService.resolveSaleInfo("SESS-BLUE");

        assertThat(info.availableForTable()).isTrue();
        assertThat(info.sessionProduct()).isTrue();
        assertThat(info.openRoshPrice()).isEqualByComparingTo("60.00");
    }

    // ── Conversão atômica entre SKUs (EST-F025) ──────────────────────────────────────────────

    /** 1 lata sai, 5 sessões entram — o caso do lounge, numa transação só. */
    @Test
    void convertStock_movesBalanceFromOneSkuToAnother() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-LATA", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "ESS-LATA", 1L, new BigDecimal("3.000"), 1L)));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SESS-BLUE", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EstoqueUseCase.StockConversionResult result = estoqueService.convertStock("ESS-LATA", "SESS-BLUE",
                new BigDecimal("1.000"), new BigDecimal("5.000"), "LOJA-01", "Fracionamento", "gerente");

        assertThat(result.from().quantity()).isEqualByComparingTo("2.000");
        assertThat(result.to().quantity()).isEqualByComparingTo("5.000");
    }

    /** O ledger precisa mostrar que as duas linhas foram o mesmo ato — daí o reason cruzado. */
    @Test
    void convertStock_writesBothSidesOfTheLedgerPointingAtEachOther() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-LATA", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "ESS-LATA", 1L, new BigDecimal("3.000"), 1L)));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SESS-BLUE", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.convertStock("ESS-LATA", "SESS-BLUE", new BigDecimal("1.000"),
                new BigDecimal("5.000"), "LOJA-01", "Fracionamento", "gerente");

        verify(stockMovementRepository).save(argThat(m -> m.type() == MovementType.SAIDA
                && m.sku().equals("ESS-LATA") && m.reason().contains("SESS-BLUE")));
        verify(stockMovementRepository).save(argThat(m -> m.type() == MovementType.ENTRADA
                && m.sku().equals("SESS-BLUE") && m.reason().contains("ESS-LATA")));
    }

    /**
     * <b>A prova da atomicidade, e o motivo da feature.</b> Sem saldo na origem, a entrada do destino
     * não pode existir — é exatamente o que os dois {@code POST /estoque/movements} soltos não
     * garantiam. Só o saldo lido é gravado; nenhum {@code save} sai daqui.
     */
    @Test
    void convertStock_withInsufficientSourceBalance_neverCreatesTheDestinationEntry() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-LATA", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "ESS-LATA", 1L, BigDecimal.ZERO, 1L)));

        assertThatThrownBy(() -> estoqueService.convertStock("ESS-LATA", "SESS-BLUE",
                new BigDecimal("1.000"), new BigDecimal("5.000"), "LOJA-01", "Fracionamento", "gerente"))
                .isInstanceOf(InsufficientStockException.class);

        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    /** Converter um SKU nele mesmo é um par de linhas que se anula — recusado antes de tocar o saldo. */
    @Test
    void convertStock_withTheSameSkuOnBothSides_isRejectedBeforeAnyWrite() {
        assertThatThrownBy(() -> estoqueService.convertStock("ESS-LATA", "ESS-LATA",
                new BigDecimal("1.000"), new BigDecimal("5.000"), "LOJA-01", "Fracionamento", "gerente"))
                .isInstanceOf(SameSkuConversionException.class);

        verifyNoInteractions(stockMovementRepository);
        verify(stockBalanceRepository, never()).save(any());
    }

    // ── Lata aberta (EST-F027) ───────────────────────────────────────────────────────────────
    //
    // Stub por teste, e não num helper compartilhado: MockitoExtension roda em STRICT_STUBS, e um
    // helper que preparasse o saldo para todos derrubaria justamente o caso que prova a feature —
    // o da sessão que NÃO toca o estoque.

    private Product essenciaDeSessao(Integer sessionsPerUnit) {
        Product produto = Product.of(1L, "ESSE-BLUE", "Zgy Blueberry", "essencia", true, List.of())
                .withPricing(Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")))
                .withAvailableForTable(true)
                .withSessionProduct(true);
        return sessionsPerUnit == null ? produto : produto.withSessionsPerUnit(sessionsPerUnit);
    }

    private OpenPackage lataAberta(int usos) {
        OpenPackage lata = OpenPackage.open("ESSE-BLUE", 1L, 5, "atendente", Instant.now());
        return usos == 0 ? lata : lata.withUses(usos);
    }

    private void comSaldoDeEssencia(String saldo) {
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESSE-BLUE", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "ESSE-BLUE", 1L, new BigDecimal(saldo), 1L)));
    }

    /**
     * <b>O bug que a feature corrige.</b> Sem lata, cada sessão baixava uma lata inteira — medido
     * no QA de 06/09/2026: 50 → 49 numa sessão só. Aqui a primeira sessão tira UMA unidade da
     * prateleira, e é a abertura da lata que a tira, não a sessão.
     */
    @Test
    void consumeSession_abreALata_baixandoUmaUnicaUnidade() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(productRepository.findByAnySku("ESSE-BLUE")).thenReturn(Optional.of(essenciaDeSessao(5)));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.empty());
        comSaldoDeEssencia("50.000");
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(openPackageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OpenPackage lata = estoqueService.consumeSession("ESSE-BLUE", "LOJA-01", BigDecimal.ONE, "atendente");

        assertThat(lata.uses()).isEqualTo(1);
        assertThat(lata.sessionsPerUnit()).isEqualTo(5);
        verify(stockMovementRepository).save(argThat(m -> m.type() == MovementType.SAIDA
                && m.sku().equals("ESSE-BLUE")
                && m.quantity().compareTo(BigDecimal.ONE) == 0
                && m.reason().contains("Abertura de lata")));
    }

    /**
     * A prova do outro lado: com lata aberta e ainda com folga, <b>nenhuma</b> unidade sai do
     * saldo. É esta linha que faz cinco sessões consumirem uma lata em vez de cinco.
     */
    @Test
    void consumeSession_comLataAberta_naoTocaOEstoque() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(productRepository.findByAnySku("ESSE-BLUE")).thenReturn(Optional.of(essenciaDeSessao(5)));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.of(lataAberta(2)));
        when(openPackageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OpenPackage lata = estoqueService.consumeSession("ESSE-BLUE", "LOJA-01", BigDecimal.ONE, "atendente");

        assertThat(lata.uses()).isEqualTo(3);
        verifyNoInteractions(stockMovementRepository);
        verify(stockBalanceRepository, never()).save(any());
    }

    /** Lata esgotada é fechada como EXHAUSTED e a sessão seguinte abre outra — aí sim baixa 1. */
    @Test
    void consumeSession_comLataEsgotada_fechaAVelhaEAbreOutra() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(productRepository.findByAnySku("ESSE-BLUE")).thenReturn(Optional.of(essenciaDeSessao(5)));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.of(lataAberta(5)));
        comSaldoDeEssencia("49.000");
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(openPackageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OpenPackage nova = estoqueService.consumeSession("ESSE-BLUE", "LOJA-01", BigDecimal.ONE, "atendente");

        assertThat(nova.uses()).isEqualTo(1);
        verify(openPackageRepository).save(argThat(p ->
                p.closeReason() == OpenPackageCloseReason.EXHAUSTED && p.uses() == 5));
        verify(stockMovementRepository).save(argThat(m -> m.type() == MovementType.SAIDA));
    }

    /**
     * Produto de sessão <b>sem</b> sessionsPerUnit continua baixando unidade pelo caminho antigo —
     * é o que torna a adoção da lata uma escolha por item de catálogo, e não uma virada de chave.
     */
    @Test
    void consumeSession_comProdutoSemSessionsPerUnit_eRecusado() {
        when(productRepository.findByAnySku("ESSE-BLUE")).thenReturn(Optional.of(essenciaDeSessao(null)));

        assertThatThrownBy(() -> estoqueService.consumeSession("ESSE-BLUE", "LOJA-01",
                BigDecimal.ONE, "atendente"))
                .isInstanceOf(NotAPackagedSessionProductException.class);

        verifyNoInteractions(stockMovementRepository);
        verifyNoInteractions(openPackageRepository);
    }

    /** Sem saldo para abrir a lata, a sessão não acontece — é o InsufficientStock de sempre. */
    @Test
    void consumeSession_semSaldoParaAbrir_falha() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(productRepository.findByAnySku("ESSE-BLUE")).thenReturn(Optional.of(essenciaDeSessao(5)));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.empty());
        comSaldoDeEssencia("0.000");

        assertThatThrownBy(() -> estoqueService.consumeSession("ESSE-BLUE", "LOJA-01",
                BigDecimal.ONE, "atendente"))
                .isInstanceOf(InsufficientStockException.class);

        verify(openPackageRepository, never()).save(any());
    }

    /**
     * "Repor essência": a lata velha fecha como REPLACED com a sobra registrada, e a nova baixa
     * uma unidade. A sobra <b>não</b> vira ajuste de perda — a unidade já tinha saído do saldo na
     * abertura, e medir o resto seria inventar um número que ninguém mediu.
     */
    @Test
    void replaceOpenPackage_fechaAVelhaComSobra_eBaixaUmaUnidadeNaNova() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(productRepository.findByAnySku("ESSE-BLUE")).thenReturn(Optional.of(essenciaDeSessao(5)));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.of(lataAberta(2)));
        comSaldoDeEssencia("49.000");
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(openPackageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OpenPackage nova = estoqueService.replaceOpenPackage("ESSE-BLUE", "LOJA-01", "atendente");

        assertThat(nova.uses()).isZero();
        verify(openPackageRepository).save(argThat(p ->
                p.closeReason() == OpenPackageCloseReason.REPLACED && p.uses() == 2));
        // Um único movimento sai daqui, o da lata nova: a sobra não gera ajuste.
        verify(stockMovementRepository, times(1)).save(argThat(m -> m.type() == MovementType.SAIDA
                && m.quantity().compareTo(BigDecimal.ONE) == 0));
    }

    /**
     * Sem saldo para a lata nova, a antiga <b>continua aberta</b> — o atendente segue com o que
     * tem na mão em vez de ficar sem lata nenhuma no sistema. É por isso que a abertura vem antes
     * do fechamento.
     */
    @Test
    void replaceOpenPackage_semSaldo_naoFechaALataAtual() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(productRepository.findByAnySku("ESSE-BLUE")).thenReturn(Optional.of(essenciaDeSessao(5)));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.of(lataAberta(2)));
        comSaldoDeEssencia("0.000");

        assertThatThrownBy(() -> estoqueService.replaceOpenPackage("ESSE-BLUE", "LOJA-01", "atendente"))
                .isInstanceOf(InsufficientStockException.class);

        verify(openPackageRepository, never()).save(any());
    }

    /**
     * Cancelamento de comanda: o contador volta, o estoque <b>não</b>. Devolver unidade aqui
     * inventaria saldo — a essência já foi queimada e não voltou para a prateleira.
     */
    @Test
    void releaseSession_decrementaOContador_semDevolverUnidade() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.of(lataAberta(3)));
        when(openPackageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.releaseSession("ESSE-BLUE", "LOJA-01", BigDecimal.ONE);

        verify(openPackageRepository).save(argThat(p -> p.uses() == 2));
        verifyNoInteractions(stockMovementRepository);
    }

    /** Lata já reposta entre o lançamento e o cancelamento: nada a desfazer, e não é erro. */
    @Test
    void releaseSession_semLataAberta_naoFazNada() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.empty());

        estoqueService.releaseSession("ESSE-BLUE", "LOJA-01", BigDecimal.ONE);

        verify(openPackageRepository, never()).save(any());
        verifyNoInteractions(stockMovementRepository);
    }

    /** 404 é "abra uma, é só lançar a sessão" — distinto do 400 de produto que não usa lata. */
    @Test
    void findOpenPackage_semLataAberta_lanca404() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(openPackageRepository.findOpen("ESSE-BLUE", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.findOpenPackage("ESSE-BLUE", "LOJA-01"))
                .isInstanceOf(OpenPackageNotFoundException.class);
    }

    // ── Exclusão de rascunho (EST-F026) ──────────────────────────────────────────────────────

    @Test
    void deleteProduct_apagaORascunhoSemHistorico() {
        Product rascunho = Product.of(1L, "SKU-DRAFT", "Rascunho", "essencia", true, List.of())
                .withStatus(ProductStatus.RASCUNHO);
        when(productRepository.findBySku("SKU-DRAFT")).thenReturn(Optional.of(rascunho));
        when(stockBalanceRepository.existsBySku("SKU-DRAFT")).thenReturn(false);
        when(stockMovementRepository.existsBySku("SKU-DRAFT")).thenReturn(false);

        estoqueService.deleteProduct("SKU-DRAFT");

        verify(productRepository).deleteBySku("SKU-DRAFT");
    }

    /** Produto publicado não é excluível — o caminho continua sendo active:false. */
    @Test
    void deleteProduct_comProdutoAtivo_eRecusado() {
        Product ativo = Product.of(1L, "SKU-001", "Produto", "essencia", true, List.of());
        when(productRepository.findBySku("SKU-001")).thenReturn(Optional.of(ativo));

        assertThatThrownBy(() -> estoqueService.deleteProduct("SKU-001"))
                .isInstanceOf(ProductNotDraftException.class);

        verify(productRepository, never()).deleteBySku(any());
    }

    /** Rascunho que chegou a movimentar estoque tem histórico que ficaria órfão (EST-C011). */
    @Test
    void deleteProduct_comMovimentacaoGravada_eRecusado() {
        Product rascunho = Product.of(1L, "SKU-DRAFT", "Rascunho", "essencia", true, List.of())
                .withStatus(ProductStatus.RASCUNHO);
        when(productRepository.findBySku("SKU-DRAFT")).thenReturn(Optional.of(rascunho));
        when(stockBalanceRepository.existsBySku("SKU-DRAFT")).thenReturn(false);
        when(stockMovementRepository.existsBySku("SKU-DRAFT")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.deleteProduct("SKU-DRAFT"))
                .isInstanceOf(ProductHasStockHistoryException.class);

        verify(productRepository, never()).deleteBySku(any());
    }

    @Test
    void deleteProduct_comSkuInexistente_lanca404() {
        when(productRepository.findBySku("SUMIU")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.deleteProduct("SUMIU"))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
