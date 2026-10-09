package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.in.converter.CategoryDTOConverter;
import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.converter.StockCountDTOConverter;
import com.cernecommerce.adapter.in.converter.StockMovementDTOConverter;
import com.cernecommerce.adapter.in.converter.StockReservationDTOConverter;
import com.cernecommerce.adapter.in.converter.WarehouseDTOConverter;
import com.cernecommerce.core.domain.exception.storage.ImageTooLargeException;
import com.cernecommerce.core.domain.exception.storage.InvalidImageFormatException;
import com.cernecommerce.core.domain.exception.estoque.BarcodeNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.BrandHasProductsException;
import com.cernecommerce.core.domain.exception.estoque.BrandNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateAttributeTypeNameException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateBarcodeException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateBrandNameException;
import com.cernecommerce.core.domain.exception.estoque.ReplenishmentItemNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.VariantHasStockHistoryException;
import com.cernecommerce.core.domain.exception.estoque.DraftLimitReachedException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException;
import com.cernecommerce.core.domain.exception.estoque.LotExpiryDateMismatchException;
import com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductVariantNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ReservedStockException;
import com.cernecommerce.core.domain.exception.estoque.InvalidOpenPackageUsesException;
import com.cernecommerce.core.domain.exception.estoque.InvalidPackagingException;
import com.cernecommerce.core.domain.exception.estoque.PackagingNotFoundException;
import com.cernecommerce.core.domain.model.estoque.SkuPackaging;
import com.cernecommerce.core.domain.exception.estoque.OpenPackageAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.SameSkuConversionException;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedUnitCostException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.AbcClass;
import com.cernecommerce.core.domain.model.estoque.AbcAnalysis;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.AttributeType;
import com.cernecommerce.core.domain.model.estoque.Brand;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateCategoryNameException;
import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.CategoryProductCount;
import com.cernecommerce.core.domain.model.estoque.EstoqueSummary;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockCountStatus;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.ProductImageUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class EstoqueControllerTest {

    private MockMvc mockMvc;
    private EstoqueUseCase estoqueUseCase;
    private ProductImageUseCase productImageUseCase;
    /** Campo, e não variável local, desde EST-F025: há teste que verifica o evento publicado. */
    private ApplicationEventPublisher publisher;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        estoqueUseCase = mock(EstoqueUseCase.class);
        productImageUseCase = mock(ProductImageUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        com.cernecommerce.core.ports.in.ComprasUseCase comprasUseCase =
                mock(com.cernecommerce.core.ports.in.ComprasUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EstoqueController(estoqueUseCase, new ProductDTOConverter(),
                        new WarehouseDTOConverter(), new StockMovementDTOConverter(),
                        new StockCountDTOConverter(), new StockReservationDTOConverter(),
                        new CategoryDTOConverter(),
                        new com.cernecommerce.adapter.in.converter.BrandDTOConverter(),
                        productImageUseCase, publisher, comprasUseCase,
                        new com.cernecommerce.adapter.in.converter.ReplenishmentListDTOConverter()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Product product(String sku) {
        return Product.of(1L, sku, "Narguile Aladin", "narguile", true,
                List.of(ProductVariant.of(1L, sku + "-M", List.of(new ProductAttribute("sabor", "menta")), true)));
    }

    @Test
    void list_returns_200_with_products() throws Exception {
        when(estoqueUseCase.listProducts(eq(0), eq(20), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(product("NARG-001")), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].variants[0].attributes[0].type").value("sabor"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getSummary_returns_200_withAggregatedNumbers() throws Exception {
        when(estoqueUseCase.getSummary()).thenReturn(new EstoqueSummary(342L, 891L,
                new BigDecimal("128450.30"), 7L, 15L, new CategoryProductCount("Bebidas", 58)));

        mockMvc.perform(get("/estoque/summary").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProdutos").value(342))
                .andExpect(jsonPath("$.totalVariantes").value(891))
                .andExpect(jsonPath("$.valorEstoqueCusto").value(128450.30))
                .andExpect(jsonPath("$.alertasCriticos").value(7))
                .andExpect(jsonPath("$.alertasAtencao").value(15))
                .andExpect(jsonPath("$.categoriaComMaisProdutos.nome").value("Bebidas"))
                .andExpect(jsonPath("$.categoriaComMaisProdutos.quantidade").value(58));
    }

    @Test
    void getSummary_semCategoriaVinculada_categoriaComMaisProdutosVemNula() throws Exception {
        when(estoqueUseCase.getSummary()).thenReturn(new EstoqueSummary(0L, 0L, BigDecimal.ZERO, 0L, 0L, null));

        mockMvc.perform(get("/estoque/summary").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaComMaisProdutos").doesNotExist());
    }

    @Test
    void list_sem_parametros_nao_filtra_e_ordena_por_id_ascendente() throws Exception {
        // Contrato de retrocompatibilidade: quem já chamava só com page/size continua vendo o
        // mesmo comportamento de antes desta entrega.
        when(estoqueUseCase.listProducts(eq(0), eq(20), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/products")).andExpect(status().isOk());

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(estoqueUseCase).listProducts(eq(0), eq(20), filter.capture(),
                eq(ProductSortField.ID), eq(SortDirection.ASC));
        assertThat(filter.getValue().isEmpty()).isTrue();
    }

    @Test
    void list_repassa_busca_filtros_e_ordenacao_ao_use_case() throws Exception {
        when(estoqueUseCase.listProducts(eq(1), eq(50), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 1, 50, 0L, 0));

        mockMvc.perform(get("/estoque/products")
                        .param("page", "1")
                        .param("size", "50")
                        .param("search", "MeNtA")
                        .param("category", "Narguilé")
                        .param("brand", "Zomo")
                        .param("active", "true")
                        .param("sort", "NAME")
                        .param("direction", "DESC"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(estoqueUseCase).listProducts(eq(1), eq(50), filter.capture(),
                eq(ProductSortField.NAME), eq(SortDirection.DESC));
        // Normalizado pelo próprio ProductFilter.
        assertThat(filter.getValue().search()).isEqualTo("menta");
        assertThat(filter.getValue().category()).isEqualTo("narguilé");
        assertThat(filter.getValue().brand()).isEqualTo("zomo");
        assertThat(filter.getValue().active()).isTrue();
    }

    @Test
    void list_com_active_false_filtra_inativos_em_vez_de_ignorar() throws Exception {
        when(estoqueUseCase.listProducts(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/products").param("active", "false"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(estoqueUseCase).listProducts(anyInt(), anyInt(), filter.capture(), any(), any());
        assertThat(filter.getValue().active()).isFalse();
    }

    @Test
    void list_com_status_filtra_por_rascunho() throws Exception {
        when(estoqueUseCase.listProducts(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/products").param("status", "RASCUNHO"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(estoqueUseCase).listProducts(anyInt(), anyInt(), filter.capture(), any(), any());
        assertThat(filter.getValue().status()).isEqualTo(ProductStatus.RASCUNHO);
    }

    @Test
    void list_com_sort_invalido_returns_400_listando_os_valores_aceitos() throws Exception {
        mockMvc.perform(get("/estoque/products").param("sort", "custo_secreto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ENUM_VALUE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SALE_PRICE")));

        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void getProduct_returns_200_para_sku_existente() throws Exception {
        when(estoqueUseCase.findProductBySku("NARG-001")).thenReturn(product("NARG-001"));

        mockMvc.perform(get("/estoque/products/NARG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NARG-001"))
                .andExpect(jsonPath("$.name").value("Narguile Aladin"));
    }

    @Test
    void getProduct_de_sku_inexistente_returns_404() throws Exception {
        when(estoqueUseCase.findProductBySku("SUMIU-001"))
                .thenThrow(new ProductNotFoundException("SUMIU-001"));

        mockMvc.perform(get("/estoque/products/SUMIU-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void getProduct_nao_colide_com_a_rota_literal_de_reorder_points() throws Exception {
        // /estoque/products/reorder-points é rota literal e precisa continuar vencendo o
        // template /estoque/products/{sku} — senão "reorder-points" viraria uma busca por SKU.
        when(estoqueUseCase.listReorderPoints(anyString(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/products/reorder-points").param("warehouseCode", "PRINCIPAL"))
                .andExpect(status().isOk());

        verify(estoqueUseCase, never()).findProductBySku(anyString());
    }

    @Test
    void create_returns_201() throws Exception {
        Product created = product("NARG-001");
        when(estoqueUseCase.createProduct(eq("NARG-001"), eq("Narguile Aladin"), eq("narguile"), any(), any(), any(),
                any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        String body = "{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\",\"category\":\"narguile\","
                + "\"variants\":[{\"sku\":\"NARG-001-M\",\"attributes\":[{\"type\":\"sabor\",\"value\":\"menta\"}]}]}";

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("NARG-001"));
    }

    @Test
    void create_comStatusRascunho_repassaAoUseCaseESerializaNaResposta() throws Exception {
        Product criado = product("RASC-001").withStatus(ProductStatus.RASCUNHO);
        when(estoqueUseCase.createProduct(eq("RASC-001"), eq("Só o essencial"), any(), any(), any(), any(),
                any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(),
                eq(ProductStatus.RASCUNHO), any(), any())).thenReturn(criado);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"RASC-001\",\"name\":\"Só o essencial\",\"status\":\"RASCUNHO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
    }

    @Test
    void create_rascunhoNoLimite_returns_409() throws Exception {
        when(estoqueUseCase.createProduct(anyString(), anyString(), any(), any(), any(), any(),
                any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(),
                eq(ProductStatus.RASCUNHO), any(), any()))
                .thenThrow(new DraftLimitReachedException());

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"RASC-006\",\"name\":\"Sexto rascunho\",\"status\":\"RASCUNHO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_LIMIT_REACHED"));
    }

    @Test
    void update_comStatusAtivo_repassaAoUseCase() throws Exception {
        Product atualizado = product("RASC-001");
        when(estoqueUseCase.updateProduct(eq("RASC-001"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(ProductStatus.ATIVO), any(), any())).thenReturn(atualizado);

        mockMvc.perform(patch("/estoque/products/RASC-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ATIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void changeSku_returns200AndAuditsOldAndNew() throws Exception {
        when(estoqueUseCase.changeSku("NARG-001", "NARG-900")).thenReturn(product("NARG-900"));

        mockMvc.perform(patch("/estoque/products/NARG-001/sku")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSku\":\" NARG-900 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NARG-900"));

        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(e ->
                e instanceof com.cernecommerce.core.domain.event.AuditEvent a
                        && a.type() == com.cernecommerce.core.domain.event.AuditEvent.EventType.PRODUCT_SKU_CHANGED));
    }

    @Test
    void changeSku_duplicateReturns409() throws Exception {
        when(estoqueUseCase.changeSku("NARG-001", "NARG-002"))
                .thenThrow(new com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException("NARG-002"));

        mockMvc.perform(patch("/estoque/products/NARG-001/sku")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSku\":\"NARG-002\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void changeSku_blankReturns400() throws Exception {
        mockMvc.perform(patch("/estoque/products/NARG-001/sku")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSku\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(estoqueUseCase, never()).changeSku(any(), any());
    }

    @Test
    void create_without_sku_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Narguile Aladin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicate_sku_returns_409() throws Exception {
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateSkuException("NARG-001"));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SKU_ALREADY_EXISTS"));
    }

    @Test
    void create_product_without_variants_returns_201() throws Exception {
        Product created = Product.of(2L, "CARV-001", "Carvão Coco", "carvao", true, List.of());
        when(estoqueUseCase.createProduct(eq("CARV-001"), eq("Carvão Coco"), eq("carvao"), any(), any(), any(),
                any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"CARV-001\",\"name\":\"Carvão Coco\",\"category\":\"carvao\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants").isEmpty());
    }

    @Test
    void createProduct_duplicateBarcode_returns_409() throws Exception {
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateBarcodeException("7891234567895"));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\",\"barcode\":\"7891234567895\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BARCODE_ALREADY_EXISTS"));
    }

    @Test
    void getProductByBarcode_returns_200() throws Exception {
        when(estoqueUseCase.findProductByBarcode("7891234567895")).thenReturn(product("NARG-001"));

        mockMvc.perform(get("/estoque/products/by-barcode/7891234567895").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NARG-001"));
    }

    @Test
    void getProductByBarcode_notFound_returns_404() throws Exception {
        when(estoqueUseCase.findProductByBarcode("00000000")).thenThrow(new BarcodeNotFoundException("00000000"));

        mockMvc.perform(get("/estoque/products/by-barcode/00000000").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BARCODE_NOT_FOUND"));
    }

    @Test
    void createWarehouse_returns_201() throws Exception {
        Warehouse created = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(estoqueUseCase.createWarehouse("LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA)).thenReturn(created);

        mockMvc.perform(post("/estoque/warehouses")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LOJA-01\",\"name\":\"Loja Centro\",\"type\":\"LOJA_FISICA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LOJA-01"))
                .andExpect(jsonPath("$.type").value("LOJA_FISICA"));
    }

    @Test
    void createWarehouse_duplicateCode_returns_409() throws Exception {
        when(estoqueUseCase.createWarehouse(eq("LOJA-01"), any(), any()))
                .thenThrow(new DuplicateWarehouseCodeException("LOJA-01"));

        mockMvc.perform(post("/estoque/warehouses")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LOJA-01\",\"name\":\"Loja Centro\",\"type\":\"LOJA_FISICA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_CODE_ALREADY_EXISTS"));
    }

    @Test
    void createWarehouse_withoutCode_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/warehouses")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Loja Centro\",\"type\":\"LOJA_FISICA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWarehouse_withInvalidType_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/warehouses")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LOJA-01\",\"name\":\"Loja Centro\",\"type\":\"INEXISTENTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWarehouses_returns_200_paginated() throws Exception {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(estoqueUseCase.listWarehouses(0, 20))
                .thenReturn(new PageResult<>(List.of(warehouse), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("LOJA-01"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listWarehouses_honoursPageAndSize() throws Exception {
        when(estoqueUseCase.listWarehouses(2, 5))
                .thenReturn(new PageResult<>(List.of(), 2, 5, 12L, 3));

        mockMvc.perform(get("/estoque/warehouses").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).listWarehouses(2, 5);
    }

    @Test
    void getStockBalance_returns_200() throws Exception {
        when(estoqueUseCase.getStockBalance("NARG-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("3.000"), 1L));

        mockMvc.perform(get("/estoque/stock-balance").param("sku", "NARG-001").param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NARG-001"))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.quantity").value(3.0));
    }

    @Test
    void getStockBalance_warehouseNotFound_returns_404() throws Exception {
        when(estoqueUseCase.getStockBalance("NARG-001", "INEXISTENTE"))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(get("/estoque/stock-balance").param("sku", "NARG-001").param("warehouseCode", "INEXISTENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void registerMovement_returns_201_withUpdatedBalance() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin")))
                .thenReturn(StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 1L));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("NARG-001"))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.quantity").value(5.0));
    }

    @Test
    void registerMovement_comLote_passaLoteAdianteERetorna201() throws Exception {
        when(estoqueUseCase.adjustStock(eq("ESS-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin"), eq("LOTE-A"),
                eq(LocalDate.of(2027, 3, 1)), isNull()))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, new BigDecimal("5.000"), 1L));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\",\"lotCode\":\"LOTE-A\","
                                + "\"expiryDate\":\"2027-03-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ESS-001"));
    }

    @Test
    void registerMovement_loteObrigatorioAusente_returns_400() throws Exception {
        when(estoqueUseCase.adjustStock(eq("ESS-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), any(), any()))
                .thenThrow(new MissingLotInfoException("ESS-001"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("LOT_INFO_REQUIRED"));
    }

    @Test
    void registerMovement_loteInesperado_returns_400() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin"), eq("LOTE-A"),
                eq(LocalDate.of(2027, 3, 1)), isNull()))
                .thenThrow(new UnexpectedLotInfoException("NARG-001", "produto não é lote-rastreado"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\",\"lotCode\":\"LOTE-A\","
                                + "\"expiryDate\":\"2027-03-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("LOT_INFO_NOT_APPLICABLE"));
    }

    @Test
    void registerMovement_validadeDivergente_returns_409() throws Exception {
        when(estoqueUseCase.adjustStock(eq("ESS-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin"), eq("LOTE-A"),
                eq(LocalDate.of(2027, 3, 1)), isNull()))
                .thenThrow(new LotExpiryDateMismatchException("ESS-001", "LOTE-A",
                        LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 1)));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\",\"lotCode\":\"LOTE-A\","
                                + "\"expiryDate\":\"2027-03-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LOT_EXPIRY_MISMATCH"));
    }

    @Test
    void registerMovement_comUnitCost_passaCustoAdianteERetorna201() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("5.000")), eq("Recebimento"), eq("admin"), isNull(), isNull(),
                eq(new BigDecimal("7.50"))))
                .thenReturn(StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"),
                        BigDecimal.ZERO, new BigDecimal("7.50"), 1L));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\",\"unitCost\":7.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.averageCost").value(7.50));
    }

    @Test
    void registerMovement_unitCostInesperado_returns_400() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(new BigDecimal("1.000")), eq("Venda"), eq("admin"), isNull(), isNull(),
                eq(new BigDecimal("7.50"))))
                .thenThrow(new UnexpectedUnitCostException("NARG-001", "custo só se aplica a ENTRADA"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"SAIDA\","
                                + "\"quantity\":1.000,\"reason\":\"Venda\",\"unitCost\":7.50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNIT_COST_NOT_APPLICABLE"));
    }

    @Test
    void registerMovement_withInvalidType_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"INEXISTENTE\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMovement_withoutQuantity_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"reason\":\"Recebimento\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMovement_withNegativeQuantity_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":-1,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMovement_warehouseNotFound_returns_404() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("INEXISTENTE"), any(), any(), any(), any()))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"INEXISTENTE\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void registerMovement_skuNotInCatalog_returns_404() throws Exception {
        when(estoqueUseCase.adjustStock(eq("SKU-FANTASMA"), any(), any(), any(), any(), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-FANTASMA\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void setReorderPoint_skuNotInCatalog_returns_404() throws Exception {
        doThrow(new ProductNotFoundException("SKU-FANTASMA"))
                .when(estoqueUseCase).setReorderPoint(eq("SKU-FANTASMA"), any(), any());

        mockMvc.perform(put("/estoque/products/SKU-FANTASMA/reorder-point")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\",\"minQuantity\":10.000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void createProduct_dataIntegrityViolation_returns_409_withoutLeakingDriverMessage() throws Exception {
        // EST-C010: rede de segurança — antes, qualquer violação de constraint virava 500 com o
        // texto do driver (nome de tabela, constraint e valores da linha) no corpo da resposta.
        when(estoqueUseCase.createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint \"uk_product_variant_sku\""));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\",\"category\":\"narguile\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("uk_product_variant_sku"))));
    }

    @Test
    void registerMovement_insufficientStock_returns_400() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.SAIDA), any(), any(), any()))
                .thenThrow(new InsufficientStockException("NARG-001", 1L, new BigDecimal("2.000"), new BigDecimal("5.000")));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"SAIDA\","
                                + "\"quantity\":5.000,\"reason\":\"Venda\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    /**
     * EST-C016 — o par de {@code INSUFFICIENT_STOCK}. Aqui o físico BASTARIA (10 na prateleira
     * para uma saída de 5), mas 8 estão reservados para um pedido online e o disponível é 2.
     * Antes deste handler a exceção caía no fallback e virava 500, escondendo do operador
     * justamente a informação que resolve o caso — quanto está reservado, e para quê.
     */
    @Test
    void registerMovement_reservedStock_returns_400() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.SAIDA), any(), any(), any()))
                .thenThrow(new ReservedStockException("NARG-001", 1L, new BigDecimal("10.000"),
                        new BigDecimal("8.000"), new BigDecimal("5.000")));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"SAIDA\","
                                + "\"quantity\":5.000,\"reason\":\"Venda\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESERVED_STOCK"))
                // A mensagem do domínio tem que sobreviver: é ela que diz quanto está reservado.
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reservado 8.000")));
    }

    // ── Curva ABC (EST-F011) ─────────────────────────────────────────────────────────────────

    @Test
    void abcAnalysis_returns_200_withClassifiedLines() throws Exception {
        when(estoqueUseCase.findAbcAnalysis(any(), any(), any())).thenReturn(List.of(
                new AbcAnalysis.AbcEntry("ESS-CARA", "Essência Cara", new BigDecimal("2"),
                        new BigDecimal("900.00"), new BigDecimal("90.00"), AbcClass.A, new BigDecimal("0.20")),
                new AbcAnalysis.AbcEntry("CARVAO", "Carvão", new BigDecimal("100"),
                        new BigDecimal("100.00"), new BigDecimal("100.00"), AbcClass.C, null)));

        mockMvc.perform(get("/estoque/analytics/abc")
                        .principal(AUTH)
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("ESS-CARA"))
                .andExpect(jsonPath("$[0].abcClass").value("A"))
                .andExpect(jsonPath("$[0].cumulativePercent").value(90.00))
                // Saldo zero devolve giro nulo, não um número enorme.
                .andExpect(jsonPath("$[1].abcClass").value("C"))
                .andExpect(jsonPath("$[1].turnover").doesNotExist());
    }

    @Test
    void abcAnalysis_withoutPeriod_returns_400() throws Exception {
        mockMvc.perform(get("/estoque/analytics/abc").principal(AUTH))
                .andExpect(status().isBadRequest());
    }

    // ── Conversão atômica entre SKUs (EST-F025) ──────────────────────────────────────────────

    @Test
    void convertStock_returns_201_withBothBalances() throws Exception {
        StockBalance from = StockBalance.of(1L, "ESS-LATA", 1L, new BigDecimal("2.000"), 1L);
        StockBalance to = StockBalance.of(2L, "SESS-BLUE", 1L, new BigDecimal("5.000"), 1L);
        when(estoqueUseCase.convertStock(eq("ESS-LATA"), eq("SESS-BLUE"), any(), any(), eq("LOJA-01"),
                any(), any())).thenReturn(new EstoqueUseCase.StockConversionResult(from, to));

        mockMvc.perform(post("/estoque/conversions")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromSku\":\"ESS-LATA\",\"toSku\":\"SESS-BLUE\",\"fromQuantity\":1.000,"
                                + "\"toQuantity\":5.000,\"warehouseCode\":\"LOJA-01\",\"reason\":\"Fracionamento\"}"))
                .andExpect(status().isCreated())
                // As duas pontas na resposta: é a pergunta que o operador faz ao converter.
                .andExpect(jsonPath("$.from.sku").value("ESS-LATA"))
                .andExpect(jsonPath("$.from.quantity").value(2.000))
                .andExpect(jsonPath("$.to.sku").value("SESS-BLUE"))
                .andExpect(jsonPath("$.to.quantity").value(5.000));
    }

    @Test
    void convertStock_withSameSkuOnBothSides_returns_400() throws Exception {
        when(estoqueUseCase.convertStock(eq("ESS-LATA"), eq("ESS-LATA"), any(), any(), any(), any(), any()))
                .thenThrow(new SameSkuConversionException("ESS-LATA"));

        mockMvc.perform(post("/estoque/conversions")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromSku\":\"ESS-LATA\",\"toSku\":\"ESS-LATA\",\"fromQuantity\":1.000,"
                                + "\"toQuantity\":5.000,\"warehouseCode\":\"LOJA-01\",\"reason\":\"Fracionamento\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SAME_SKU_CONVERSION"));
    }

    /** Sem saldo na origem o erro é o mesmo de qualquer saída — e nada do destino foi criado. */
    @Test
    void convertStock_withInsufficientSourceBalance_returns_400() throws Exception {
        when(estoqueUseCase.convertStock(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientStockException("ESS-LATA", 1L, BigDecimal.ZERO, BigDecimal.ONE));

        mockMvc.perform(post("/estoque/conversions")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromSku\":\"ESS-LATA\",\"toSku\":\"SESS-BLUE\",\"fromQuantity\":1.000,"
                                + "\"toQuantity\":5.000,\"warehouseCode\":\"LOJA-01\",\"reason\":\"Fracionamento\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    /** Um evento para o ato, não um por movimento — ver o comentário no controller. */
    @Test
    void convertStock_publishesASingleStockConvertedEvent() throws Exception {
        StockBalance from = StockBalance.of(1L, "ESS-LATA", 1L, new BigDecimal("2.000"), 1L);
        StockBalance to = StockBalance.of(2L, "SESS-BLUE", 1L, new BigDecimal("5.000"), 1L);
        when(estoqueUseCase.convertStock(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EstoqueUseCase.StockConversionResult(from, to));

        mockMvc.perform(post("/estoque/conversions")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromSku\":\"ESS-LATA\",\"toSku\":\"SESS-BLUE\",\"fromQuantity\":1.000,"
                                + "\"toQuantity\":5.000,\"warehouseCode\":\"LOJA-01\",\"reason\":\"Fracionamento\"}"))
                .andExpect(status().isCreated());

        verify(publisher, times(1)).publishEvent(argThat((Object e) ->
                e instanceof AuditEvent audit && audit.type() == AuditEvent.EventType.STOCK_CONVERTED));
    }

    // ── Lata já aberta antes do sistema (EST-F033) ───────────────────────────────────────────

    private static final String CADASTRO_DE_LATA = "{\"warehouseCode\":\"LOJA-01\",\"usesRemaining\":2}";

    @Test
    void registerOpenPackage_returns_201_withTheCounter() throws Exception {
        when(estoqueUseCase.registerOpenPackage("ESSE-BLUE", "LOJA-01", 2, "admin"))
                .thenReturn(OpenPackage.registered("ESSE-BLUE", 1L, 5, 2, "admin", Instant.now()));
        // O nome da lata vem de findProductBySku (ler o nome não é venda — EST-F036).
        when(estoqueUseCase.findProductBySku("ESSE-BLUE"))
                .thenReturn(Product.of(1L, "ESSE-BLUE", "Essência Blueberry", "Essências", true, List.of()));

        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CADASTRO_DE_LATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ESSE-BLUE"))
                .andExpect(jsonPath("$.uses").value(3))
                .andExpect(jsonPath("$.remaining").value(2))
                .andExpect(jsonPath("$.sessionsPerUnit").value(5));
    }

    /**
     * O cadastro é a única entrada no contador que não deixa rastro em {@code stock_movement} —
     * por isso o evento é dele, e não emprestado do {@code OPEN_PACKAGE_REPLACED}.
     */
    @Test
    void registerOpenPackage_publishesOpenPackageRegistered() throws Exception {
        when(estoqueUseCase.registerOpenPackage(any(), any(), anyInt(), any()))
                .thenReturn(OpenPackage.registered("ESSE-BLUE", 1L, 5, 2, "admin", Instant.now()));
        // O nome da lata vem de findProductBySku (ler o nome não é venda — EST-F036).
        when(estoqueUseCase.findProductBySku("ESSE-BLUE"))
                .thenReturn(Product.of(1L, "ESSE-BLUE", "Essência Blueberry", "Essências", true, List.of()));

        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CADASTRO_DE_LATA))
                .andExpect(status().isCreated());

        verify(publisher, times(1)).publishEvent(argThat((Object e) ->
                e instanceof AuditEvent audit && audit.type() == AuditEvent.EventType.OPEN_PACKAGE_REGISTERED));
    }

    @Test
    void registerOpenPackage_withAPackageAlreadyOpen_returns_409() throws Exception {
        when(estoqueUseCase.registerOpenPackage(any(), any(), anyInt(), any()))
                .thenThrow(new OpenPackageAlreadyOpenException("ESSE-BLUE", "LOJA-01"));

        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CADASTRO_DE_LATA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPEN_PACKAGE_ALREADY_OPEN"));
    }

    @Test
    void registerOpenPackage_withMoreRemainingThanThePackageHolds_returns_400() throws Exception {
        when(estoqueUseCase.registerOpenPackage(any(), any(), anyInt(), any()))
                .thenThrow(new InvalidOpenPackageUsesException(6, 5));

        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\",\"usesRemaining\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OPEN_PACKAGE_INVALID_USES"));
    }

    /** Sem restante, ou zero: barrado no DTO, antes de chegar ao service. */
    @Test
    void registerOpenPackage_withoutUsesRemaining_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\",\"usesRemaining\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\"}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).registerOpenPackage(any(), any(), anyInt(), any());
    }

    // ── Produto base com variações (EST-F036) ────────────────────────────────────────────────

    @Test
    void setParentSellable_returns_200_withTheFlag_andPublishesProductUpdated() throws Exception {
        when(estoqueUseCase.setParentSellable("NARG-001", true))
                .thenReturn(product("NARG-001").withParentSellable(true));

        mockMvc.perform(patch("/estoque/products/NARG-001/parent-sellable")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentSellable\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentSellable").value(true));

        verify(publisher).publishEvent(argThat((Object e) ->
                e instanceof AuditEvent audit && audit.type() == AuditEvent.EventType.PRODUCT_UPDATED));
    }

    @Test
    void setParentSellable_withoutTheField_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/products/NARG-001/parent-sellable")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** O produto devolve o flag: é o que o PDV usa para esconder a base na busca. */
    @Test
    void productResponse_carriesParentSellable() throws Exception {
        when(estoqueUseCase.findProductBySku("NARG-001")).thenReturn(product("NARG-001"));

        mockMvc.perform(get("/estoque/products/NARG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentSellable").value(false));
    }

    // ── Embalagem (EST-F032) ─────────────────────────────────────────────────────────────────

    @Test
    void definePackaging_returns_200_andPublishesPackagingDefined() throws Exception {
        when(estoqueUseCase.definePackaging("LM-AZUL-UN", "LM-AZUL-MACO", 20))
                .thenReturn(new SkuPackaging("LM-AZUL-UN", "LM-AZUL-MACO", 20));

        mockMvc.perform(put("/estoque/products/LM-AZUL-UN/packaging")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentSku\":\"LM-AZUL-MACO\",\"unitsPerParent\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childSku").value("LM-AZUL-UN"))
                .andExpect(jsonPath("$.parentSku").value("LM-AZUL-MACO"))
                .andExpect(jsonPath("$.unitsPerParent").value(20));

        verify(publisher).publishEvent(argThat((Object e) ->
                e instanceof AuditEvent audit && audit.type() == AuditEvent.EventType.PACKAGING_DEFINED));
    }

    @Test
    void definePackaging_withACycle_returns_400() throws Exception {
        when(estoqueUseCase.definePackaging(any(), any(), anyInt()))
                .thenThrow(new InvalidPackagingException("ligação criaria um ciclo"));

        mockMvc.perform(put("/estoque/products/LM-AZUL-MACO/packaging")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentSku\":\"LM-AZUL-UN\",\"unitsPerParent\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PACKAGING"));
    }

    /** Fator 1 não é embalagem: barrado no DTO. */
    @Test
    void definePackaging_withFactorOne_returns_400() throws Exception {
        mockMvc.perform(put("/estoque/products/LM-AZUL-UN/packaging")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentSku\":\"LM-AZUL-MACO\",\"unitsPerParent\":1}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).definePackaging(any(), any(), anyInt());
    }

    @Test
    void removePackaging_returns_204_andWithoutLink_404() throws Exception {
        mockMvc.perform(delete("/estoque/products/LM-AZUL-UN/packaging").principal(AUTH))
                .andExpect(status().isNoContent());
        verify(publisher).publishEvent(argThat((Object e) ->
                e instanceof AuditEvent audit && audit.type() == AuditEvent.EventType.PACKAGING_REMOVED));

        doThrow(new PackagingNotFoundException("LM-AZUL-MACO")).when(estoqueUseCase).removePackaging("LM-AZUL-MACO");
        mockMvc.perform(delete("/estoque/products/LM-AZUL-MACO/packaging").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PACKAGING_NOT_FOUND"));
    }

    @Test
    void getPackagingChain_returnsTheLevelsWithBalances() throws Exception {
        when(estoqueUseCase.getPackagingChain("LM-AZUL-MACO", "LOJA-01")).thenReturn(List.of(
                new EstoqueUseCase.PackagingLevel("LM-AZUL-CART", "LM-AZUL-MACO", 10, new BigDecimal("1")),
                new EstoqueUseCase.PackagingLevel("LM-AZUL-MACO", "LM-AZUL-UN", 20, new BigDecimal("8")),
                new EstoqueUseCase.PackagingLevel("LM-AZUL-UN", null, null, new BigDecimal("15"))));

        mockMvc.perform(get("/estoque/products/LM-AZUL-MACO/packaging").param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("LM-AZUL-CART"))
                .andExpect(jsonPath("$[0].containsUnits").value(10))
                .andExpect(jsonPath("$[2].containsSku").doesNotExist())
                .andExpect(jsonPath("$[2].available").value(15));
    }

    @Test
    void setReorderPoint_returns_204() throws Exception {
        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\",\"minQuantity\":10.000}"))
                .andExpect(status().isNoContent());

        verify(estoqueUseCase).setReorderPoint("NARG-001", "LOJA-01", new BigDecimal("10.000"));
    }

    @Test
    void setReorderPoint_withoutWarehouseCode_returns_400() throws Exception {
        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minQuantity\":10.000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setReorderPoint_warehouseNotFound_returns_404() throws Exception {
        doThrow(new WarehouseNotFoundException("INEXISTENTE"))
                .when(estoqueUseCase).setReorderPoint(eq("NARG-001"), eq("INEXISTENTE"), any());

        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"INEXISTENTE\",\"minQuantity\":10.000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    // ── Kits (EST-F015) ──────────────────────────────────────────────────────────────────────

    @Test
    void defineKitRecipe_returns_200_withPromotedProduct() throws Exception {
        Product kit = Product.of(1L, "KIT-001", "Kit Narguile", "combo", true, List.of(),
                Pricing.empty(), com.cernecommerce.core.domain.model.estoque.ProductType.KIT);
        when(estoqueUseCase.defineKitRecipe(eq("KIT-001"), any())).thenReturn(kit);

        mockMvc.perform(put("/estoque/products/KIT-001/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[{\"componentSku\":\"CARV-001\",\"quantity\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("KIT"));
    }

    @Test
    void defineKitRecipe_withoutComponents_returns_400() throws Exception {
        mockMvc.perform(put("/estoque/products/KIT-001/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defineKitRecipe_kitSkuNotFound_returns_404() throws Exception {
        when(estoqueUseCase.defineKitRecipe(eq("SKU-FANTASMA"), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(put("/estoque/products/SKU-FANTASMA/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[{\"componentSku\":\"CARV-001\",\"quantity\":2}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void defineKitRecipe_selfReference_returns_409() throws Exception {
        when(estoqueUseCase.defineKitRecipe(eq("KIT-001"), any()))
                .thenThrow(new com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException("KIT-001"));

        mockMvc.perform(put("/estoque/products/KIT-001/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[{\"componentSku\":\"KIT-001\",\"quantity\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("KIT_SELF_REFERENCE"));
    }

    @Test
    void defineKitRecipe_componentNotSimples_returns_409() throws Exception {
        when(estoqueUseCase.defineKitRecipe(eq("KIT-001"), any()))
                .thenThrow(new com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException(
                        "KIT-001", "KIT-002", com.cernecommerce.core.domain.model.estoque.ProductType.KIT));

        mockMvc.perform(put("/estoque/products/KIT-001/kit")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"components\":[{\"componentSku\":\"KIT-002\",\"quantity\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("KIT_COMPONENT_NOT_SIMPLE"));
    }

    @Test
    void getKitRecipe_returns_200_withComponentList() throws Exception {
        when(estoqueUseCase.getKitRecipeDetailed("KIT-001")).thenReturn(List.of(
                new com.cernecommerce.core.domain.model.estoque.KitComponentDetail("CARV-001",
                        new BigDecimal("2"), "Carvão Coco", null, new BigDecimal("10"), true)));

        mockMvc.perform(get("/estoque/products/KIT-001/kit").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].componentSku").value("CARV-001"))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    void getKitRecipe_skuNotFound_returns_404() throws Exception {
        when(estoqueUseCase.getKitRecipeDetailed("SKU-FANTASMA")).thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(get("/estoque/products/SKU-FANTASMA/kit").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void listStockLots_returns_200_withLotList() throws Exception {
        when(estoqueUseCase.listStockLots("ESS-001", "LOJA-01")).thenReturn(List.of(
                com.cernecommerce.core.domain.model.estoque.StockLot.of(1L, "ESS-001", 1L, "LOTE-A",
                        java.time.LocalDate.of(2027, 1, 1), new BigDecimal("5.000"), null, 0L)));

        mockMvc.perform(get("/estoque/products/ESS-001/lots").param("warehouseCode", "LOJA-01").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("ESS-001"))
                .andExpect(jsonPath("$[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$[0].lotCode").value("LOTE-A"))
                .andExpect(jsonPath("$[0].quantity").value(5.0))
                .andExpect(jsonPath("$[0].alertedAt").isEmpty());
    }

    @Test
    void listStockLots_semLoteRecebido_returns_200_withEmptyList() throws Exception {
        when(estoqueUseCase.listStockLots("NARG-001", "LOJA-01")).thenReturn(List.of());

        mockMvc.perform(get("/estoque/products/NARG-001/lots").param("warehouseCode", "LOJA-01").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listStockLots_warehouseNotFound_returns_404() throws Exception {
        when(estoqueUseCase.listStockLots("ESS-001", "INEXISTENTE"))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(get("/estoque/products/ESS-001/lots").param("warehouseCode", "INEXISTENTE").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    private StockMovement movement(long id, MovementType type, String quantity, String reason) {
        return StockMovement.of(id, "NARG-001", 1L, type, new BigDecimal(quantity), reason, "gerente",
                Instant.parse("2026-07-26T12:00:00Z"));
    }

    @Test
    void listMovements_returns_200_with_ledger() throws Exception {
        when(estoqueUseCase.listMovements("NARG-001", "LOJA-01", null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(
                        movement(9L, MovementType.SAIDA, "2.000", "Venda balcão sessão #7"),
                        movement(8L, MovementType.ENTRADA, "20.000", "Recebimento de mercadoria - fornecedor #3")),
                        0, 20, 2L, 1));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.content[0].type").value("SAIDA"))
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].username").value("gerente"))
                .andExpect(jsonPath("$.content[0].reason").value("Venda balcão sessão #7"))
                .andExpect(jsonPath("$.content[1].type").value("ENTRADA"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listMovements_returns_200_withEmptyPageWhenNeverMoved() throws Exception {
        when(estoqueUseCase.listMovements("SEM-USO", "LOJA-01", null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "SEM-USO")
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * O teto de 100 deixou de ser {@code Math.min} silencioso e virou {@code @Max(100)} → 400
     * (EST-C005). Como a validação de parâmetro de handler é nativa do
     * {@code RequestMappingHandlerAdapter} e não do controller em si, ela é exercitada com
     * contexto real em {@code EstoqueControllerValidationTest}, não aqui no standalone.
     */
    @Test
    void listMovements_passesPageAndSizeThroughUnchanged() throws Exception {
        when(estoqueUseCase.listMovements("NARG-001", "LOJA-01", null, null, null, 1, 100))
                .thenReturn(new PageResult<>(List.of(), 1, 100, 0L, 0));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "LOJA-01")
                        .param("page", "1")
                        .param("size", "100"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).listMovements("NARG-001", "LOJA-01", null, null, null, 1, 100);
    }

    /** {@code sku} é opcional: omitido, filtra só por depósito — não é mais 400. */
    @Test
    void listMovements_withoutSku_filtersByWarehouseOnly() throws Exception {
        when(estoqueUseCase.listMovements(null, "LOJA-01", null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(estoqueUseCase).listMovements(null, "LOJA-01", null, null, null, 0, 20);
    }

    /**
     * {@code warehouseCode} é opcional: omitido, o feed geral resolve o código de cada depósito
     * distinto na página a partir do {@code warehouseId} da movimentação.
     */
    @Test
    void listMovements_withoutWarehouseCode_resolvesWarehouseCodePerMovement() throws Exception {
        when(estoqueUseCase.listMovements("NARG-001", null, null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(movement(9L, MovementType.SAIDA, "2.000", "Venda balcão")),
                        0, 20, 1L, 1));
        when(estoqueUseCase.getWarehouse(1L))
                .thenReturn(Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"));
    }

    /** Sem nenhum filtro, alimenta o feed geral de movimentações. */
    @Test
    void listMovements_withoutAnyFilter_returns_200() throws Exception {
        when(estoqueUseCase.listMovements(null, null, null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/movements").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listMovements_warehouseNotFound_returns_404() throws Exception {
        when(estoqueUseCase.listMovements("NARG-001", "INEXISTENTE", null, null, null, 0, 20))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(get("/estoque/movements")
                        .principal(AUTH)
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "INEXISTENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void listOrphanSkus_returns_200_with_diagnosticRows() throws Exception {
        OrphanSku orphan = OrphanSku.of("SKU-FANTASMA", "LOJA-01", new BigDecimal("3.000"), 2L, true,
                Instant.parse("2026-07-01T10:00:00Z"));
        when(estoqueUseCase.listOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(orphan), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/integrity/orphan-skus").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("SKU-FANTASMA"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].quantity").value(3.0))
                .andExpect(jsonPath("$.content[0].movementCount").value(2))
                .andExpect(jsonPath("$.content[0].hasReorderPoint").value(true))
                .andExpect(jsonPath("$.content[0].lastMovementAt").exists())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Órfão que nunca foi movimentado: saldo/ponto de reposição sem ledger. */
    @Test
    void listOrphanSkus_serializesNullLastMovementAt() throws Exception {
        OrphanSku orphan = OrphanSku.of("SKU-FANTASMA", "LOJA-01", new BigDecimal("1.000"), 0L, false, null);
        when(estoqueUseCase.listOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(orphan), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/integrity/orphan-skus").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastMovementAt").isEmpty())
                .andExpect(jsonPath("$.content[0].movementCount").value(0));
    }

    /** Base íntegra é 200 com página vazia — não é 404. */
    @Test
    void listOrphanSkus_returns_200_withEmptyPageWhenBaseIsClean() throws Exception {
        when(estoqueUseCase.listOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/integrity/orphan-skus").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------------------------------
    // EST-F013/EST-F021 — reserva de estoque (listagem) e EST-C013 (integridade)
    // ------------------------------------------------------------------------------------

    @Test
    void listReservations_returns_200_withWarehouseCodeResolved() throws Exception {
        Warehouse loja = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockReservation reservation = StockReservation.of(10L, "NARG-001", 1L, new BigDecimal("2.000"),
                "CHECKOUT:abc", ReservationStatus.ACTIVE, Instant.parse("2026-07-29T12:30:00Z"),
                Instant.parse("2026-07-29T12:00:00Z"), null, "cliente@exemplo.com");
        when(estoqueUseCase.listReservations(null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(reservation), 0, 20, 1L, 1));
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(loja);

        mockMvc.perform(get("/estoque/reservations").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].ownerReference").value("CHECKOUT:abc"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listReservations_passesFiltersThrough() throws Exception {
        when(estoqueUseCase.listReservations("NARG-001", "LOJA-01", ReservationStatus.ACTIVE, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/reservations")
                        .principal(AUTH)
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "LOJA-01")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).listReservations("NARG-001", "LOJA-01", ReservationStatus.ACTIVE, 0, 20);
    }

    @Test
    void getReservation_returns_200() throws Exception {
        Warehouse loja = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockReservation reservation = StockReservation.of(10L, "NARG-001", 1L, new BigDecimal("2.000"),
                "CHECKOUT:abc", ReservationStatus.ACTIVE, Instant.parse("2026-07-29T12:30:00Z"),
                Instant.parse("2026-07-29T12:00:00Z"), null, "cliente@exemplo.com");
        when(estoqueUseCase.getStockReservation(10L)).thenReturn(reservation);
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(loja);

        mockMvc.perform(get("/estoque/reservations/10").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"));
    }

    @Test
    void getReservation_returns_404_whenNotFound() throws Exception {
        when(estoqueUseCase.getStockReservation(999L))
                .thenThrow(new StockReservationNotFoundException(999L));

        mockMvc.perform(get("/estoque/reservations/999").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void listReservationMismatches_returns_200_withDiagnosticRows() throws Exception {
        ReservationIntegrityMismatch mismatch = ReservationIntegrityMismatch.of("NARG-001", "LOJA-01",
                new BigDecimal("5.000"), new BigDecimal("3.000"));
        when(estoqueUseCase.listReservationMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(mismatch), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/integrity/reservation-mismatch").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].reservedQuantity").value(5.0))
                .andExpect(jsonPath("$.content[0].activeReservationsTotal").value(3.0))
                .andExpect(jsonPath("$.content[0].difference").value(2.0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Base íntegra é 200 com página vazia — não é 404, mesma convenção de listOrphanSkus. */
    @Test
    void listReservationMismatches_returns_200_withEmptyPageWhenIntegrityIsFine() throws Exception {
        when(estoqueUseCase.listReservationMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/integrity/reservation-mismatch").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listLotMismatches_returns_200_withDiagnosticRows() throws Exception {
        com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch mismatch =
                com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch.of("ESS-001", "LOJA-01",
                        new BigDecimal("10.000"), new BigDecimal("6.000"));
        when(estoqueUseCase.listLotMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(mismatch), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/integrity/lot-mismatch").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("ESS-001"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.content[0].balanceQuantity").value(10.0))
                .andExpect(jsonPath("$.content[0].lotsTotal").value(6.0))
                .andExpect(jsonPath("$.content[0].difference").value(4.0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Base íntegra é 200 com página vazia — não é 404, mesma convenção dos outros diagnósticos. */
    @Test
    void listLotMismatches_returns_200_withEmptyPageWhenIntegrityIsFine() throws Exception {
        when(estoqueUseCase.listLotMismatches(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/estoque/integrity/lot-mismatch").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ------------------------------------------------------------------------------------
    // EST-F018 — PATCH e desativação
    // ------------------------------------------------------------------------------------

    @Test
    void updateProduct_returns_200_withUpdatedBody() throws Exception {
        when(estoqueUseCase.updateProduct("NARG-001", "Narguilé Aladin 2.0", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                EstoqueUseCase.TableSessionCommand.EMPTY))
                .thenReturn(Product.of(1L, "NARG-001", "Narguilé Aladin 2.0", "narguile", true, List.of()));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Narguilé Aladin 2.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Narguilé Aladin 2.0"))
                .andExpect(jsonPath("$.sku").value("NARG-001"));

        verify(estoqueUseCase).updateProduct("NARG-001", "Narguilé Aladin 2.0", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                EstoqueUseCase.TableSessionCommand.EMPTY);
    }

    /** Corpo vazio é um no-op válido: nenhum campo veio, nada muda. */
    @Test
    void updateProduct_comCorpoVazio_naoAlteraNada() throws Exception {
        when(estoqueUseCase.updateProduct("NARG-001", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, EstoqueUseCase.TableSessionCommand.EMPTY))
                .thenReturn(product("NARG-001"));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).updateProduct("NARG-001", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, EstoqueUseCase.TableSessionCommand.EMPTY);
    }

    @Test
    void updateProduct_skuInexistente_returns_404() throws Exception {
        when(estoqueUseCase.updateProduct(eq("SKU-FANTASMA"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(patch("/estoque/products/SKU-FANTASMA")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void updateProduct_comNomeVazio_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).updateProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    // ------------------------------------------------------------------------------------
    // EST-F019 — precificação
    // ------------------------------------------------------------------------------------

    @Test
    void createProduct_comPricing_repassaAoUseCase() throws Exception {
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Product.of(1L, "NARG-001", "Narguile", "narguile", true, List.of(),
                        Pricing.of(new BigDecimal("45.00"), new BigDecimal("80"), new BigDecimal("79.90"))));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\","
                                + "\"pricing\":{\"costPrice\":45.00,\"markupPercent\":80,\"salePrice\":79.90}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pricing.costPrice").value(45.00))
                .andExpect(jsonPath("$.pricing.effectivePrice").value(79.90));

        ArgumentCaptor<Pricing> captor = ArgumentCaptor.forClass(Pricing.class);
        verify(estoqueUseCase).createProduct(eq("NARG-001"), any(), any(), any(), captor.capture(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue().costPrice()).isEqualByComparingTo("45.00");
    }

    /** Produto sem preço serializa o bloco com os campos nulos — nunca um `pricing` ausente. */
    @Test
    void listProducts_produtoSemPreco_serializaPricingComCamposNulos() throws Exception {
        when(estoqueUseCase.listProducts(eq(0), eq(20), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(product("NARG-001")), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pricing").exists())
                .andExpect(jsonPath("$.content[0].pricing.costPrice").doesNotExist())
                .andExpect(jsonPath("$.content[0].pricing.priced").value(false));
    }

    @Test
    void updateProduct_comPricing_repassaOBlocoAoUseCase() throws Exception {
        when(estoqueUseCase.updateProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Product.of(1L, "NARG-001", "Narguile", "narguile", true, List.of(),
                        Pricing.of(new BigDecimal("60.00"), new BigDecimal("80"), new BigDecimal("79.90"))));

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricing\":{\"costPrice\":60.00}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.costPrice").value(60.00));

        ArgumentCaptor<Pricing> captor = ArgumentCaptor.forClass(Pricing.class);
        verify(estoqueUseCase).updateProduct(eq("NARG-001"), any(), any(), captor.capture(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue().costPrice()).isEqualByComparingTo("60.00");
        assertThat(captor.getValue().markupPercent()).as("campo ausente vira nulo = manter").isNull();
    }

    @Test
    void createProduct_comCustoNegativo_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\","
                                + "\"pricing\":{\"costPrice\":-1.00}}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    // ------------------------------------------------------------------------------------
    // Campos de marketing: originalPrice, superPromo, description, videoUrl, images
    // ------------------------------------------------------------------------------------

    @Test
    void createProduct_comCamposDeMarketing_repassaAoUseCaseERetornaNoBody() throws Exception {
        Product created = Product.create("NARG-001", "Narguile", "narguile", List.of(),
                Pricing.of(new BigDecimal("45.00"), null, new BigDecimal("79.90"), new BigDecimal("99.90")),
                ProductType.SIMPLES, false, "Aladin", "http://img.png", true, true, "Descrição longa",
                "http://video.mp4", List.of("http://img1.png", "http://img2.png"));
        when(estoqueUseCase.createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\",\"category\":\"narguile\","
                                + "\"superPromo\":true,\"description\":\"Descrição longa\","
                                + "\"videoUrl\":\"http://video.mp4\","
                                + "\"images\":[\"http://img1.png\",\"http://img2.png\"],"
                                + "\"pricing\":{\"costPrice\":45.00,\"salePrice\":79.90,\"originalPrice\":99.90}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.superPromo").value(true))
                .andExpect(jsonPath("$.description").value("Descrição longa"))
                .andExpect(jsonPath("$.videoUrl").value("http://video.mp4"))
                .andExpect(jsonPath("$.images[0]").value("http://img1.png"))
                .andExpect(jsonPath("$.images[1]").value("http://img2.png"))
                .andExpect(jsonPath("$.pricing.originalPrice").value(99.90))
                .andExpect(jsonPath("$.pricing.hasDiscount").value(true));

        ArgumentCaptor<Boolean> superPromoCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(estoqueUseCase).createProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), anyBoolean(),
                superPromoCaptor.capture(), eq("Descrição longa"), eq("http://video.mp4"),
                eq(List.of("http://img1.png", "http://img2.png")), any(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(superPromoCaptor.getValue()).isTrue();
    }

    @Test
    void createProduct_descriptionAcimaDoLimite_returns_400() throws Exception {
        String descricaoMuitoLonga = "x".repeat(5001);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\","
                                + "\"description\":\"" + descricaoMuitoLonga + "\"}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any());
    }

    @Test
    void createProduct_maisDeCincoImagens_returns_400() throws Exception {
        String images = "[\"http://a.png\",\"http://b.png\",\"http://c.png\",\"http://d.png\","
                + "\"http://e.png\",\"http://f.png\"]";

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\",\"images\":" + images + "}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any());
    }

    @Test
    void createProduct_urlDeImagemAcimaDoLimite_returns_400() throws Exception {
        String urlMuitoLonga = "http://img.png/" + "x".repeat(2048);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile\",\"images\":[\""
                                + urlMuitoLonga + "\"]}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).createProduct(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any());
    }

    @Test
    void updateProduct_comCamposDeMarketing_repassaAoUseCase() throws Exception {
        Product updated = Product.of(1L, "NARG-001", "Narguile", "narguile", true, List.of(),
                Pricing.empty(), ProductType.SIMPLES, false, null, null, false, true, "Nova descrição",
                "http://video.mp4", List.of("http://img1.png"));
        when(estoqueUseCase.updateProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"superPromo\":true,\"description\":\"Nova descrição\","
                                + "\"videoUrl\":\"http://video.mp4\",\"images\":[\"http://img1.png\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superPromo").value(true))
                .andExpect(jsonPath("$.description").value("Nova descrição"))
                .andExpect(jsonPath("$.videoUrl").value("http://video.mp4"))
                .andExpect(jsonPath("$.images[0]").value("http://img1.png"));

        verify(estoqueUseCase).updateProduct(eq("NARG-001"), any(), any(), any(), any(), any(), any(), eq(true),
                eq("Nova descrição"), eq("http://video.mp4"), eq(List.of("http://img1.png")), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateProduct_descriptionAcimaDoLimite_returns_400() throws Exception {
        String descricaoMuitoLonga = "x".repeat(5001);

        mockMvc.perform(patch("/estoque/products/NARG-001")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + descricaoMuitoLonga + "\"}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).updateProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void getProductPrice_returns_200_comDerivados() throws Exception {
        when(estoqueUseCase.findPricingBySku("NARG-001"))
                .thenReturn(Pricing.of(new BigDecimal("45.00"), new BigDecimal("80"), new BigDecimal("79.90")));

        mockMvc.perform(get("/estoque/products/NARG-001/price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedPrice").value(81.00))
                .andExpect(jsonPath("$.effectivePrice").value(79.90))
                .andExpect(jsonPath("$.marginAmount").value(34.90))
                .andExpect(jsonPath("$.marginPercent").value(43.68))
                .andExpect(jsonPath("$.effectiveMarkupPercent").value(77.56))
                .andExpect(jsonPath("$.priced").value(true))
                .andExpect(jsonPath("$.belowCost").value(false));
    }

    @Test
    void getProductPrice_produtoSemPreco_returns_200_comCamposNulos() throws Exception {
        when(estoqueUseCase.findPricingBySku("NARG-001")).thenReturn(Pricing.empty());

        mockMvc.perform(get("/estoque/products/NARG-001/price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectivePrice").doesNotExist())
                .andExpect(jsonPath("$.priced").value(false));
    }

    @Test
    void getProductPrice_skuInexistente_returns_404() throws Exception {
        when(estoqueUseCase.findPricingBySku("SKU-FANTASMA"))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(get("/estoque/products/SKU-FANTASMA/price"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void setProductActive_returns_200() throws Exception {
        when(estoqueUseCase.setProductActive("NARG-001", false))
                .thenReturn(Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", false, List.of()));

        mockMvc.perform(patch("/estoque/products/NARG-001/active")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    /** {@code Boolean} + {@code @NotNull} para o corpo vazio não virar um "desativar" silencioso. */
    @Test
    void setProductActive_semOCampoActive_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/products/NARG-001/active")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).setProductActive(any(), anyBoolean());
    }

    @Test
    void setProductLotTracked_returns_200() throws Exception {
        when(estoqueUseCase.setProductLotTracked("ESS-001", true))
                .thenReturn(Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of(),
                        Pricing.empty(), ProductType.SIMPLES, true));

        mockMvc.perform(patch("/estoque/products/ESS-001/lot-tracked")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotTracked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotTracked").value(true));
    }

    @Test
    void setProductLotTracked_semOCampo_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/products/ESS-001/lot-tracked")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).setProductLotTracked(any(), anyBoolean());
    }

    @Test
    void setProductLotTracked_emKit_returns_400() throws Exception {
        when(estoqueUseCase.setProductLotTracked("KIT-001", true))
                .thenThrow(new IllegalArgumentException(
                        "kit não pode ser lote-rastreado: kit não tem saldo físico próprio (EST-F015)"));

        mockMvc.perform(patch("/estoque/products/KIT-001/lot-tracked")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotTracked\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWarehouse_returns_200() throws Exception {
        when(estoqueUseCase.updateWarehouse("LOJA-01", "Loja Reformada", null))
                .thenReturn(Warehouse.of(1L, "LOJA-01", "Loja Reformada", WarehouseType.LOJA_FISICA, true));

        mockMvc.perform(patch("/estoque/warehouses/LOJA-01")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Loja Reformada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Loja Reformada"))
                .andExpect(jsonPath("$.type").value("LOJA_FISICA"));
    }

    @Test
    void updateWarehouse_comTipoDesconhecido_returns_400() throws Exception {
        mockMvc.perform(patch("/estoque/warehouses/LOJA-01")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GALPAO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWarehouse_inexistente_returns_404() throws Exception {
        when(estoqueUseCase.updateWarehouse(eq("INEXISTENTE"), any(), any()))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(patch("/estoque/warehouses/INEXISTENTE")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void setWarehouseActive_returns_200() throws Exception {
        when(estoqueUseCase.setWarehouseActive("LOJA-01", false))
                .thenReturn(Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, false));

        mockMvc.perform(patch("/estoque/warehouses/LOJA-01/active")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void registerMovement_entradaEmProdutoDesativado_returns_409() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), any(), any()))
                .thenThrow(new InactiveProductException("NARG-001"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_INACTIVE"));
    }

    @Test
    void registerMovement_entradaEmDepositoDesativado_returns_409() throws Exception {
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                any(), any(), any()))
                .thenThrow(new InactiveWarehouseException("LOJA-01"));

        mockMvc.perform(post("/estoque/movements")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                                + "\"quantity\":5.000,\"reason\":\"Recebimento\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_INACTIVE"));
    }

    // ------------------------------------------------------------------------------------
    // EST-F006 — balanço de inventário
    // ------------------------------------------------------------------------------------

    private static final Warehouse LOJA =
            Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);

    private StockCount count(StockCountStatus status, List<StockCountItem> items) {
        return StockCount.of(50L, 1L, status, "gerente",
                Instant.parse("2026-07-27T09:00:00Z"),
                status == StockCountStatus.ABERTA ? null : Instant.parse("2026-07-27T18:00:00Z"), items);
    }

    @Test
    void openStockCount_returns_201_withLocation() throws Exception {
        when(estoqueUseCase.openStockCount("LOJA-01", "admin"))
                .thenReturn(count(StockCountStatus.ABERTA, List.of()));

        mockMvc.perform(post("/estoque/stock-counts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/estoque/stock-counts/50"))
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void openStockCount_segundoNoMesmoDeposito_returns_409() throws Exception {
        when(estoqueUseCase.openStockCount(eq("LOJA-01"), any()))
                .thenThrow(new StockCountAlreadyOpenException("LOJA-01", 50L));

        mockMvc.perform(post("/estoque/stock-counts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"LOJA-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_ALREADY_OPEN"));
    }

    @Test
    void openStockCount_depositoInexistente_returns_404() throws Exception {
        when(estoqueUseCase.openStockCount(eq("INEXISTENTE"), any()))
                .thenThrow(new WarehouseNotFoundException("INEXISTENTE"));

        mockMvc.perform(post("/estoque/stock-counts")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"INEXISTENTE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void recordCountedItem_returns_200() throws Exception {
        StockCount updated = count(StockCountStatus.ABERTA,
                List.of(StockCountItem.of(1L, "NARG-001", new BigDecimal("37.000"), null, null)));
        when(estoqueUseCase.recordCountedItem(50L, "NARG-001", new BigDecimal("37.000"), null)).thenReturn(updated);
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(post("/estoque/stock-counts/50/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"countedQuantity\":37.000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("NARG-001"))
                .andExpect(jsonPath("$.items[0].countedQuantity").value(37.0))
                .andExpect(jsonPath("$.items[0].expectedQuantity").isEmpty())
                .andExpect(jsonPath("$.warehouseCode").value("LOJA-01"));
    }

    /** Contar zero é o item que sumiu — o teto inferior é inclusivo de propósito. */
    @Test
    void recordCountedItem_contagemZero_returns_200() throws Exception {
        StockCount updated = count(StockCountStatus.ABERTA,
                List.of(StockCountItem.of(1L, "NARG-001", BigDecimal.ZERO, null, null)));
        when(estoqueUseCase.recordCountedItem(eq(50L), eq("NARG-001"), any(), isNull())).thenReturn(updated);
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(post("/estoque/stock-counts/50/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"countedQuantity\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void recordCountedItem_contagemNegativa_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/50/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"countedQuantity\":-1}"))
                .andExpect(status().isBadRequest());

        verify(estoqueUseCase, never()).recordCountedItem(any(), any(), any(), any());
    }

    @Test
    void recordCountedItem_balancoFechado_returns_409() throws Exception {
        when(estoqueUseCase.recordCountedItem(eq(50L), any(), any(), any()))
                .thenThrow(new StockCountNotOpenException(50L, StockCountStatus.FECHADA));

        mockMvc.perform(post("/estoque/stock-counts/50/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NARG-001\",\"countedQuantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_OPEN"));
    }

    @Test
    void closeStockCount_returns_200_withDivergences() throws Exception {
        StockCount closed = count(StockCountStatus.FECHADA, List.of(
                StockCountItem.of(1L, "SKU-FALTA", new BigDecimal("8.000"),
                        new BigDecimal("10.000"), new BigDecimal("-2.000"))));
        when(estoqueUseCase.closeStockCount(50L, "admin")).thenReturn(closed);
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(post("/estoque/stock-counts/50/close").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].expectedQuantity").value(10.0))
                .andExpect(jsonPath("$.items[0].difference").value(-2.0));
    }

    @Test
    void closeStockCount_balancoJaFechado_returns_409() throws Exception {
        when(estoqueUseCase.closeStockCount(eq(50L), any()))
                .thenThrow(new StockCountNotOpenException(50L, StockCountStatus.FECHADA));

        mockMvc.perform(post("/estoque/stock-counts/50/close").principal(AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_OPEN"));
    }

    @Test
    void closeStockCount_inexistente_returns_404() throws Exception {
        when(estoqueUseCase.closeStockCount(eq(99L), any()))
                .thenThrow(new StockCountNotFoundException(99L));

        mockMvc.perform(post("/estoque/stock-counts/99/close").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_FOUND"));
    }

    @Test
    void cancelStockCount_returns_200() throws Exception {
        when(estoqueUseCase.cancelStockCount(50L))
                .thenReturn(count(StockCountStatus.CANCELADA, List.of()));
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(post("/estoque/stock-counts/50/cancel").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void getStockCount_returns_200() throws Exception {
        when(estoqueUseCase.getStockCount(50L)).thenReturn(count(StockCountStatus.ABERTA, List.of()));
        when(estoqueUseCase.getWarehouse(1L)).thenReturn(LOJA);

        mockMvc.perform(get("/estoque/stock-counts/50").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.username").value("gerente"));
    }

    @Test
    void getStockCount_inexistente_returns_404() throws Exception {
        when(estoqueUseCase.getStockCount(99L)).thenThrow(new StockCountNotFoundException(99L));

        mockMvc.perform(get("/estoque/stock-counts/99").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_FOUND"));
    }

    @Test
    void listStockCounts_returns_200_paginated() throws Exception {
        when(estoqueUseCase.listStockCounts("LOJA-01", 0, 20))
                .thenReturn(new PageResult<>(List.of(count(StockCountStatus.ABERTA, List.of())), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/stock-counts").principal(AUTH).param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("LOJA-01"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listStockCounts_semWarehouseCode_returns_400() throws Exception {
        mockMvc.perform(get("/estoque/stock-counts").principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
    }

    @Test
    void listOrphanSkus_passesPageAndSizeThroughUnchanged() throws Exception {
        when(estoqueUseCase.listOrphanSkus(3, 100))
                .thenReturn(new PageResult<>(List.of(), 3, 100, 0L, 0));

        mockMvc.perform(get("/estoque/integrity/orphan-skus")
                        .principal(AUTH)
                        .param("page", "3")
                        .param("size", "100"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).listOrphanSkus(3, 100);
    }

    // ===== Upload de imagem de produto =====

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11};

    @Test
    void uploadProductImage_devolve_a_url_no_body_e_repassa_os_bytes_ao_use_case() throws Exception {
        when(productImageUseCase.upload(any())).thenReturn("http://localhost:8082/product-images/abc.jpg");

        mockMvc.perform(multipart("/estoque/products/images")
                        .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", JPEG_BYTES))
                        .principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("http://localhost:8082/product-images/abc.jpg"));

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(productImageUseCase).upload(captor.capture());
        assertThat(captor.getValue()).isEqualTo(JPEG_BYTES);
    }

    @Test
    void uploadProductImage_arquivo_vazio_returns_400_sem_chamar_o_use_case() throws Exception {
        mockMvc.perform(multipart("/estoque/products/images")
                        .file(new MockMultipartFile("file", "vazio.jpg", "image/jpeg", new byte[0]))
                        .principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE_FORMAT"));

        verifyNoInteractions(productImageUseCase);
    }

    @Test
    void uploadProductImage_formato_recusado_pelo_dominio_returns_400() throws Exception {
        when(productImageUseCase.upload(any())).thenThrow(new InvalidImageFormatException());

        mockMvc.perform(multipart("/estoque/products/images")
                        .file(new MockMultipartFile("file", "script.jpg", "image/jpeg", "nao sou imagem".getBytes()))
                        .principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE_FORMAT"));
    }

    @Test
    void uploadProductImage_acima_do_limite_returns_400() throws Exception {
        when(productImageUseCase.upload(any())).thenThrow(new ImageTooLargeException(5_242_880L));

        mockMvc.perform(multipart("/estoque/products/images")
                        .file(new MockMultipartFile("file", "grande.jpg", "image/jpeg", JPEG_BYTES))
                        .principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMAGE_TOO_LARGE"));
    }

    // ===== Atributos do produto pai =====

    @Test
    void createProduct_comAtributosDeRaiz_repassaAoUseCaseESerializaNaResposta() throws Exception {
        Product criado = Product.of(1L, "ATR-001", "Essência", "essencia", true, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil")));
        when(estoqueUseCase.createProduct(anyString(), anyString(), any(), anyList(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), anyList(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(criado);

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"ATR-001","name":"Essência","category":"essencia",
                                 "attributes":[{"type":"Origem","value":"Brasil"}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attributes[0].type").value("Origem"))
                .andExpect(jsonPath("$.attributes[0].value").value("Brasil"));

        ArgumentCaptor<List<ProductAttribute>> captor = ArgumentCaptor.forClass(List.class);
        verify(estoqueUseCase).createProduct(anyString(), anyString(), any(), anyList(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), captor.capture(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue()).extracting(ProductAttribute::type).containsExactly("Origem");
    }

    @Test
    void createProduct_semAtributos_repassaListaVazia() throws Exception {
        when(estoqueUseCase.createProduct(anyString(), anyString(), any(), anyList(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), anyList(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(product("ATR-002"));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ATR-002\",\"name\":\"Carvão\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attributes").isArray());

        ArgumentCaptor<List<ProductAttribute>> captor = ArgumentCaptor.forClass(List.class);
        verify(estoqueUseCase).createProduct(anyString(), anyString(), any(), anyList(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), captor.capture(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void updateProduct_atributosAusentes_chegamComoNullEnaoComoListaVazia() throws Exception {
        // A diferença é o que separa "não mexer" de "apagar todos".
        when(estoqueUseCase.updateProduct(anyString(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(product("ATR-003"));

        mockMvc.perform(patch("/estoque/products/ATR-003")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo Nome\"}"))
                .andExpect(status().isOk());

        verify(estoqueUseCase).updateProduct(eq("ATR-003"), eq("Novo Nome"), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), isNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateProduct_atributosComListaVazia_chegamComoListaVaziaParaLimpar() throws Exception {
        when(estoqueUseCase.updateProduct(anyString(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(product("ATR-004"));

        mockMvc.perform(patch("/estoque/products/ATR-004")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attributes\":[]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<ProductAttribute>> captor = ArgumentCaptor.forClass(List.class);
        verify(estoqueUseCase).updateProduct(anyString(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), captor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue()).isNotNull().isEmpty();
    }

    @Test
    void createProduct_atributoSemTipo_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"ATR-005","name":"Essência",
                                 "attributes":[{"value":"Brasil"}]}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void createProduct_maisDeVinteAtributos_returns_400() throws Exception {
        StringBuilder attrs = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            attrs.append(i > 0 ? "," : "").append("{\"type\":\"T").append(i).append("\",\"value\":\"V\"}");
        }

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ATR-006\",\"name\":\"X\",\"attributes\":[" + attrs + "]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(estoqueUseCase);
    }

    // ===== Preço por variação (EST-F020) =====

    @Test
    void createProduct_comPricingNaVariante_repassaAoUseCase() throws Exception {
        when(estoqueUseCase.createProduct(anyString(), anyString(), any(), anyList(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), anyList(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(product("VAR-001"));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"VAR-001","name":"Essência","pricing":{"salePrice":30.00},
                                 "variants":[
                                   {"sku":"VAR-001-50G"},
                                   {"sku":"VAR-001-100G","pricing":{"salePrice":99.90}}]}"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<List<ProductVariant>> captor = ArgumentCaptor.forClass(List.class);
        verify(estoqueUseCase).createProduct(anyString(), anyString(), any(), captor.capture(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), anyList(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThat(captor.getValue())
                .extracting(ProductVariant::sku, ProductVariant::hasOwnPricing)
                .containsExactly(tuple("VAR-001-50G", false), tuple("VAR-001-100G", true));
        assertThat(captor.getValue().get(1).pricing().salePrice()).isEqualByComparingTo("99.90");
    }

    @Test
    void response_variacaoSemPrecoProprio_traPricingNuloEComPrecoTraOBloco() throws Exception {
        // A ausência do bloco é o que sinaliza a herança — por isso não pode virar um objeto
        // vazio na serialização.
        Product criado = Product.of(1L, "VAR-002", "Essência", "essencia", true,
                List.of(ProductVariant.of(9L, "VAR-002-50G", List.of(), true),
                        ProductVariant.of(10L, "VAR-002-100G", List.of(), true,
                                Pricing.of(null, null, new BigDecimal("99.90")))),
                Pricing.of(null, null, new BigDecimal("30.00")));
        when(estoqueUseCase.findProductBySku("VAR-002")).thenReturn(criado);

        mockMvc.perform(get("/estoque/products/VAR-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].pricing").doesNotExist())
                .andExpect(jsonPath("$.variants[1].pricing.salePrice").value(99.90));
    }

    @Test
    void createProduct_pricingDaVarianteComValorNegativo_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"VAR-003","name":"Essência","variants":[
                                  {"sku":"VAR-003-A","pricing":{"salePrice":-1.00}}]}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(estoqueUseCase);
    }

    // ===== Mutação da grade de variantes pós-criação (EST-F024) =====

    @Test
    void addVariants_returns_200() throws Exception {
        Product updated = Product.of(1L, "GRADE-001", "Essência", "essencia", true,
                List.of(ProductVariant.of(1L, "GRADE-001-A", List.of(), true),
                        ProductVariant.of(2L, "GRADE-001-B", List.of(), true)));
        when(estoqueUseCase.addVariants(eq("GRADE-001"), any())).thenReturn(updated);

        mockMvc.perform(post("/estoque/products/GRADE-001/variants")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sku\":\"GRADE-001-B\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(2));
    }

    @Test
    void addVariants_semVariantsNoCorpo_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/products/GRADE-002/variants")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void addVariants_produtoNaoEncontrado_returns_404() throws Exception {
        when(estoqueUseCase.addVariants(eq("SKU-FANTASMA"), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(post("/estoque/products/SKU-FANTASMA/variants")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sku\":\"SKU-FANTASMA-A\"}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void addVariants_produtoEKit_returns_409() throws Exception {
        when(estoqueUseCase.addVariants(eq("KIT-001"), any()))
                .thenThrow(new KitHasVariantsException("KIT-001"));

        mockMvc.perform(post("/estoque/products/KIT-001/variants")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sku\":\"KIT-001-A\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("KIT_HAS_VARIANTS"));
    }

    @Test
    void updateVariant_returns_200() throws Exception {
        Product updated = Product.of(1L, "GRADE-003", "Essência", "essencia", true,
                List.of(ProductVariant.of(1L, "GRADE-003-A", List.of(), false)));
        when(estoqueUseCase.updateVariant(eq("GRADE-003"), eq("GRADE-003-A"), eq(false), any(), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(patch("/estoque/products/GRADE-003/variants/GRADE-003-A")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].active").value(false));
    }

    @Test
    void updateVariant_variacaoNaoEncontrada_returns_404() throws Exception {
        when(estoqueUseCase.updateVariant(eq("GRADE-004"), eq("GRADE-004-FANTASMA"), any(), any(), any(), any()))
                .thenThrow(new ProductVariantNotFoundException("GRADE-004", "GRADE-004-FANTASMA"));

        mockMvc.perform(patch("/estoque/products/GRADE-004/variants/GRADE-004-FANTASMA")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_VARIANT_NOT_FOUND"));
    }

    @Test
    void updateVariant_corpoVazio_naoAlteraNadaERepassaTudoNulo() throws Exception {
        when(estoqueUseCase.updateVariant(eq("GRADE-005"), eq("GRADE-005-A"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Product.of(1L, "GRADE-005", "Essência", "essencia", true,
                        List.of(ProductVariant.of(1L, "GRADE-005-A", List.of(), true))));

        mockMvc.perform(patch("/estoque/products/GRADE-005/variants/GRADE-005-A")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }


    // ===== Categorias do catálogo =====

    @Test
    void createCategory_returns_201_comLocationEBody() throws Exception {
        when(estoqueUseCase.createCategory("Narguilé", true, 3))
                .thenReturn(Category.of(7L, "Narguilé", true, 3, true));

        mockMvc.perform(post("/estoque/categories")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Narguilé\",\"featured\":true,\"displayOrder\":3}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/estoque/categories/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Narguilé"))
                .andExpect(jsonPath("$.featured").value(true))
                .andExpect(jsonPath("$.displayOrder").value(3))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createCategory_nomeDuplicado_returns_409() throws Exception {
        when(estoqueUseCase.createCategory(anyString(), anyBoolean(), anyInt()))
                .thenThrow(new DuplicateCategoryNameException("Narguilé"));

        mockMvc.perform(post("/estoque/categories")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Narguilé\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_ALREADY_EXISTS"));
    }

    @Test
    void createCategory_semNome_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/categories")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featured\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void createCategory_ordemNegativa_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/categories")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void updateCategory_camposAusentes_chegamComoNull() throws Exception {
        when(estoqueUseCase.updateCategory(eq(7L), any(), any(), any()))
                .thenReturn(Category.of(7L, "Narguilé", false, 0, true));

        mockMvc.perform(patch("/estoque/categories/7")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featured\":true}"))
                .andExpect(status().isOk());

        // name e displayOrder nulos = "não mexer"; featured explícito troca.
        verify(estoqueUseCase).updateCategory(7L, null, true, null);
    }

    @Test
    void updateCategory_idInexistente_returns_404() throws Exception {
        when(estoqueUseCase.updateCategory(eq(99L), any(), any(), any()))
                .thenThrow(new CategoryNotFoundException(99L));

        mockMvc.perform(patch("/estoque/categories/99")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void setCategoryActive_returns_200() throws Exception {
        when(estoqueUseCase.setCategoryActive(7L, false))
                .thenReturn(Category.of(7L, "Narguilé", false, 0, false));

        mockMvc.perform(patch("/estoque/categories/7/active")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void listCategories_returns_200_paginado() throws Exception {
        when(estoqueUseCase.listCategories(0, 20)).thenReturn(new PageResult<>(
                List.of(Category.of(7L, "Narguilé", true, 0, true)), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/categories").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Narguilé"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ===== Marcas do catálogo =====

    @Test
    void listBrands_returns_200_paginado() throws Exception {
        when(estoqueUseCase.listBrands(null, 0, 20)).thenReturn(new PageResult<>(
                List.of(Brand.of(7L, "Zomo", true)), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/brands").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Zomo"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createBrand_returns_201_comLocationEBody() throws Exception {
        when(estoqueUseCase.createBrand("Zomo")).thenReturn(Brand.of(7L, "Zomo", true));

        mockMvc.perform(post("/estoque/brands")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zomo\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/estoque/brands/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Zomo"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createBrand_nomeDuplicado_returns_409() throws Exception {
        when(estoqueUseCase.createBrand(anyString())).thenThrow(new DuplicateBrandNameException("Zomo"));

        mockMvc.perform(post("/estoque/brands")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zomo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BRAND_NAME_ALREADY_EXISTS"));
    }

    @Test
    void createBrand_semNome_returns_400() throws Exception {
        mockMvc.perform(post("/estoque/brands")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void updateBrand_returns_200() throws Exception {
        when(estoqueUseCase.updateBrand(7L, "Zomo Distribuidora"))
                .thenReturn(Brand.of(7L, "Zomo Distribuidora", true));

        mockMvc.perform(patch("/estoque/brands/7")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zomo Distribuidora\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Zomo Distribuidora"));
    }

    @Test
    void updateBrand_idInexistente_returns_404() throws Exception {
        when(estoqueUseCase.updateBrand(eq(99L), any())).thenThrow(new BrandNotFoundException(99L));

        mockMvc.perform(patch("/estoque/brands/99")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BRAND_NOT_FOUND"));
    }

    @Test
    void setBrandActive_returns_200() throws Exception {
        when(estoqueUseCase.setBrandActive(7L, false)).thenReturn(Brand.of(7L, "Zomo", false));

        mockMvc.perform(patch("/estoque/brands/7/active")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteBrand_returns_204() throws Exception {
        mockMvc.perform(delete("/estoque/brands/7").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(estoqueUseCase).deleteBrand(7L);
    }

    @Test
    void deleteBrand_comProdutoVinculado_returns_409() throws Exception {
        doThrow(new BrandHasProductsException(7L, 3L)).when(estoqueUseCase).deleteBrand(7L);

        mockMvc.perform(delete("/estoque/brands/7").principal(AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BRAND_HAS_PRODUCTS"));
    }

    // ===== Vocabulário de atributos (item 5) =====

    @Test
    void listAttributeTypes_returns_200_comOsNomes() throws Exception {
        when(estoqueUseCase.listAttributeTypes())
                .thenReturn(List.of(AttributeType.of(1L, "Aroma"), AttributeType.of(2L, "Sabor")));

        mockMvc.perform(get("/estoque/attribute-types").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Aroma"))
                .andExpect(jsonPath("$[1]").value("Sabor"));
    }

    @Test
    void createAttributeType_returns_201() throws Exception {
        when(estoqueUseCase.createAttributeType("Intensidade")).thenReturn(AttributeType.of(5L, "Intensidade"));

        mockMvc.perform(post("/estoque/attribute-types")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Intensidade\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/estoque/attribute-types/5"));
    }

    @Test
    void createAttributeType_nomeDuplicado_returns_409() throws Exception {
        when(estoqueUseCase.createAttributeType(anyString()))
                .thenThrow(new DuplicateAttributeTypeNameException("Sabor"));

        mockMvc.perform(post("/estoque/attribute-types")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sabor\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTRIBUTE_TYPE_NAME_ALREADY_EXISTS"));
    }

    // ===== DELETE reorder-point (item 4) =====

    @Test
    void deleteReorderPoint_returns_204() throws Exception {
        mockMvc.perform(delete("/estoque/products/NARG-001/reorder-point")
                        .principal(AUTH)
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isNoContent());

        verify(estoqueUseCase).deleteReorderPoint("NARG-001", "LOJA-01");
    }

    @Test
    void deleteReorderPoint_depositoInexistente_returns_404() throws Exception {
        doThrow(new WarehouseNotFoundException("INEXISTENTE")).when(estoqueUseCase)
                .deleteReorderPoint("NARG-001", "INEXISTENTE");

        mockMvc.perform(delete("/estoque/products/NARG-001/reorder-point")
                        .principal(AUTH)
                        .param("warehouseCode", "INEXISTENTE"))
                .andExpect(status().isNotFound());
    }

    // ===== DELETE de variante (item 8) =====

    @Test
    void deleteVariant_returns_200() throws Exception {
        when(estoqueUseCase.deleteVariant("NARG-001", "NARG-001-M")).thenReturn(product("NARG-001"));

        mockMvc.perform(delete("/estoque/products/NARG-001/variants/NARG-001-M").principal(AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void deleteVariant_comHistoricoDeEstoque_returns_409() throws Exception {
        when(estoqueUseCase.deleteVariant("NARG-001", "NARG-001-M"))
                .thenThrow(new VariantHasStockHistoryException("NARG-001", "NARG-001-M"));

        mockMvc.perform(delete("/estoque/products/NARG-001/variants/NARG-001-M").principal(AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VARIANT_HAS_STOCK_HISTORY"));
    }

    // ===== Histórico de compras por SKU (item 2) =====

    @Test
    void listPurchaseHistory_returns_200() throws Exception {
        StockMovement entrada = movement(9L, MovementType.ENTRADA, "24.000", "Recebimento de mercadoria");
        when(estoqueUseCase.listPurchaseHistory("NARG-001", "LOJA-01", 0, 20))
                .thenReturn(new PageResult<>(List.of(entrada), 0, 20, 1L, 1));

        mockMvc.perform(get("/estoque/products/NARG-001/purchase-history")
                        .principal(AUTH)
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].quantity").value(24.0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ===== Lista de Reposição (item 1) =====

    private ReplenishmentListItem replenishmentItem(String sku) {
        return ReplenishmentListItem.of(1L, sku, 1L, "Essência Babylon 50g", "Essências", "Zomo", null,
                new BigDecimal("2.000"), new BigDecimal("10.000"), new BigDecimal("8.000"), new BigDecimal("12"),
                new BigDecimal("45.00"), new BigDecimal("24.000"), new BigDecimal("42.50"),
                Instant.parse("2026-07-01T10:00:00Z"), "pedir junto com o pedido da Zomo",
                Instant.parse("2026-08-19T14:00:00Z"), "jeff");
    }

    @Test
    void listReplenishmentItems_returns_200() throws Exception {
        when(estoqueUseCase.listReplenishmentItems("LOJA-01")).thenReturn(List.of(replenishmentItem("ESS-001")));

        mockMvc.perform(get("/estoque/replenishment-list").principal(AUTH).param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("ESS-001"))
                .andExpect(jsonPath("$[0].productName").value("Essência Babylon 50g"))
                .andExpect(jsonPath("$[0].previousPurchase.quantity").value(24.0))
                .andExpect(jsonPath("$[0].previousPurchase.unitCost").value(42.50));
    }

    @Test
    void upsertReplenishmentItem_returns_201() throws Exception {
        when(estoqueUseCase.upsertReplenishmentItem(eq("ESS-001"), eq("LOJA-01"), eq(new BigDecimal("12")),
                eq("nota"), eq("admin"))).thenReturn(replenishmentItem("ESS-001"));

        mockMvc.perform(post("/estoque/replenishment-list/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-001\",\"warehouseCode\":\"LOJA-01\",\"quantity\":12,\"note\":\"nota\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ESS-001"));
    }

    @Test
    void upsertReplenishmentItem_produtoInexistente_returns_404() throws Exception {
        when(estoqueUseCase.upsertReplenishmentItem(anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        mockMvc.perform(post("/estoque/replenishment-list/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-FANTASMA\",\"warehouseCode\":\"LOJA-01\",\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void updateReplenishmentItem_returns_200() throws Exception {
        when(estoqueUseCase.updateReplenishmentItem("ESS-001", "LOJA-01", new BigDecimal("20"), "nota nova"))
                .thenReturn(replenishmentItem("ESS-001").withQuantityAndNote(new BigDecimal("20"), "nota nova"));

        mockMvc.perform(patch("/estoque/replenishment-list/items/ESS-001")
                        .principal(AUTH)
                        .param("warehouseCode", "LOJA-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":20,\"note\":\"nota nova\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(20))
                .andExpect(jsonPath("$.note").value("nota nova"));
    }

    @Test
    void updateReplenishmentItem_semItemAnotado_returns_404() throws Exception {
        when(estoqueUseCase.updateReplenishmentItem(anyString(), anyString(), any(), any()))
                .thenThrow(new ReplenishmentItemNotFoundException("SEM-ITEM", "LOJA-01"));

        mockMvc.perform(patch("/estoque/replenishment-list/items/SEM-ITEM")
                        .principal(AUTH)
                        .param("warehouseCode", "LOJA-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REPLENISHMENT_ITEM_NOT_FOUND"));
    }

    @Test
    void deleteReplenishmentItem_returns_204() throws Exception {
        mockMvc.perform(delete("/estoque/replenishment-list/items/ESS-001")
                        .principal(AUTH)
                        .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isNoContent());

        verify(estoqueUseCase).deleteReplenishmentItem("ESS-001", "LOJA-01");
    }

    @Test
    void clearReplenishmentList_returns_204() throws Exception {
        mockMvc.perform(delete("/estoque/replenishment-list").principal(AUTH).param("warehouseCode", "LOJA-01"))
                .andExpect(status().isNoContent());

        verify(estoqueUseCase).clearReplenishmentList("LOJA-01");
    }

    @Test
    void createProduct_repassaCategoryIdAoUseCase() throws Exception {
        when(estoqueUseCase.createProduct(anyString(), anyString(), any(), anyList(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), anyList(), any(),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(product("CAT-P1"));

        mockMvc.perform(post("/estoque/products")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"CAT-P1\",\"name\":\"Produto\",\"categoryId\":7}"))
                .andExpect(status().isCreated());

        verify(estoqueUseCase).createProduct(anyString(), anyString(), any(), anyList(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any(), anyList(), eq(7L),
                any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void response_deProduto_exposeCategoryIdEONomeDenormalizado() throws Exception {
        Product comCategoria = Product.of(1L, "CAT-P2", "Produto", "Narguilé", true, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(), 7L);
        when(estoqueUseCase.findProductBySku("CAT-P2")).thenReturn(comCategoria);

        mockMvc.perform(get("/estoque/products/CAT-P2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Narguilé"))
                .andExpect(jsonPath("$.categoryId").value(7));
    }

    // ── PLAT-C049 · método não suportado ─────────────────────────────────────────────────────

    /**
     * {@code POST} numa rota que só tem {@code GET} respondia <b>500 INTERNAL_ERROR</b>, com
     * stacktrace em {@code log.error}: {@code HttpRequestMethodNotSupportedException} não tinha
     * handler e caía no catch-all. Isso conta erro de cliente como falha de servidor no
     * monitoramento — foi o que levou o QA a caçar defeito num endpoint que não existe.
     */
    @Test
    void metodoNaoSuportado_responde405ComAllow_eNao500() throws Exception {
        mockMvc.perform(post("/estoque/summary").principal(AUTH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    // ── EST-F026 · exclusão de rascunho ──────────────────────────────────────────────────────

    @Test
    void deleteProduct_responde204_ePublicaAuditoria() throws Exception {
        when(estoqueUseCase.findProductBySku("SKU-DRAFT")).thenReturn(product("SKU-DRAFT"));

        mockMvc.perform(delete("/estoque/products/SKU-DRAFT").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(estoqueUseCase).deleteProduct("SKU-DRAFT");
    }

    /** O 409 tem que instruir: a mensagem do domínio aponta o caminho do active:false. */
    @Test
    void deleteProduct_comProdutoPublicado_responde409ComOCaminhoCerto() throws Exception {
        when(estoqueUseCase.findProductBySku("SKU-001")).thenReturn(product("SKU-001"));
        doThrow(new com.cernecommerce.core.domain.exception.estoque.ProductNotDraftException("SKU-001"))
                .when(estoqueUseCase).deleteProduct("SKU-001");

        mockMvc.perform(delete("/estoque/products/SKU-001").principal(AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_DRAFT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("active")));
    }
}
