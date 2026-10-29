package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.exception.crm.DuplicateCustomerEmailException;
import com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.in.ShopUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa {@link ShopController} com MockMvc standalone, no molde de
 * {@link RegistrationControllerTest} — segurança de rota fica a cargo de
 * {@link ShopControllerSecurityTest}, aqui é só validação e mapeamento de erro.
 */
class ShopControllerTest {

    private MockMvc mockMvc;
    private ShopUseCase shopUseCase;

    @BeforeEach
    void setup() {
        shopUseCase = mock(ShopUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(exceptionHandler, "lockoutDurationMinutes", 15L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShopController(shopUseCase, publisher,
                        new com.cernecommerce.adapter.in.converter.ShopCatalogDTOConverter(),
                        new com.cernecommerce.adapter.in.converter.CategoryDTOConverter(),
                        mock(com.cernecommerce.core.ports.in.CentralPedidosUseCase.class)))
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    private Customer customer() {
        return Customer.of(7L, "Maria", "11999990000", "maria@x.com", null, "MARKETPLACE",
                Instant.now(), CustomerStage.NOVO_LEAD);
    }

    private User customerUser() {
        return User.customer("maria@x.com", "hashed", "maria@x.com", 7L, Set.of(new Role("ROLE_CUSTOMER")));
    }

    @Test
    void register_valid_returns_201_withCustomerIdAndEmail() throws Exception {
        when(shopUseCase.registerCustomer("Maria", "maria@x.com", "11999990000", "S3nha@forte"))
                .thenReturn(new ShopUseCase.CustomerRegistration(customer(), customerUser()));

        mockMvc.perform(post("/shop/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"email\":\"maria@x.com\",\"contato\":\"11999990000\",\"password\":\"S3nha@forte\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(7))
                .andExpect(jsonPath("$.email").value("maria@x.com"));
    }

    @Test
    void register_blank_nome_returns_400() throws Exception {
        mockMvc.perform(post("/shop/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"email\":\"maria@x.com\",\"password\":\"S3nha@forte\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void register_invalid_email_returns_400() throws Exception {
        mockMvc.perform(post("/shop/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"email\":\"not-an-email\",\"password\":\"S3nha@forte\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void register_weak_password_returns_400() throws Exception {
        mockMvc.perform(post("/shop/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"email\":\"maria@x.com\",\"password\":\"fraca\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void register_duplicateEmail_returns_409() throws Exception {
        when(shopUseCase.registerCustomer(anyString(), eq("maria@x.com"), any(), anyString()))
                .thenThrow(new DuplicateCustomerEmailException("maria@x.com"));

        mockMvc.perform(post("/shop/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"email\":\"maria@x.com\",\"password\":\"S3nha@forte\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void listCatalog_returnsPagedItems() throws Exception {
        ShopUseCase.CatalogItem item = new ShopUseCase.CatalogItem(
                "ESS-001", "Essência Maçã", "essencia", new BigDecimal("30.00"), true, null, false, null, false);
        when(shopUseCase.listCatalog(0, 20, null, null, null)).thenReturn(new PageResult<>(List.of(item), 0, 20, 1L, 1));

        mockMvc.perform(get("/shop/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("ESS-001"))
                .andExpect(jsonPath("$.content[0].price").value(30.00))
                .andExpect(jsonPath("$.content[0].available").value(true));
    }

    @Test
    void listCatalog_defaultWarehouseNotConfigured_returns_503() throws Exception {
        when(shopUseCase.listCatalog(0, 20, null, null, null)).thenThrow(new DefaultWarehouseNotConfiguredException());

        mockMvc.perform(get("/shop/catalog"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("DEFAULT_WAREHOUSE_NOT_CONFIGURED"));
    }

    @Test
    void getCatalogItem_returnsDetailWithVariants() throws Exception {
        ShopUseCase.CatalogVariant variant = new ShopUseCase.CatalogVariant(
                "ESS-001-MACA", List.of(new ProductAttribute("sabor", "Maçã")), true, new BigDecimal("30.00"));
        ShopUseCase.CatalogItemDetail detail = new ShopUseCase.CatalogItemDetail(
                "ESS-001", "Essência Maçã", "essencia", new BigDecimal("30.00"), true, List.of(variant), null, false,
                null, false, null, null, List.of());
        when(shopUseCase.getCatalogItem("ESS-001")).thenReturn(detail);

        mockMvc.perform(get("/shop/catalog/ESS-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("ESS-001"))
                .andExpect(jsonPath("$.variants[0].sku").value("ESS-001-MACA"))
                .andExpect(jsonPath("$.variants[0].attributes[0].value").value("Maçã"))
                // EST-F020: a vitrine recebe o preço já resolvido por variação, próprio ou
                // herdado — não reimplementa a precedência.
                .andExpect(jsonPath("$.variants[0].price").value(30.00));
    }

    @Test
    void listCatalog_expoeOriginalPriceESuperPromoNoJson() throws Exception {
        ShopUseCase.CatalogItem item = new ShopUseCase.CatalogItem(
                "ESS-001", "Essência Maçã", "essencia", new BigDecimal("30.00"), true, null, false,
                new BigDecimal("40.00"), true);
        when(shopUseCase.listCatalog(0, 20, null, null, null)).thenReturn(new PageResult<>(List.of(item), 0, 20, 1L, 1));

        mockMvc.perform(get("/shop/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalPrice").value(40.00))
                .andExpect(jsonPath("$.content[0].superPromo").value(true));
    }

    @Test
    void getCatalogItem_expoeOsCincoCamposDeMarketingNoJson() throws Exception {
        ShopUseCase.CatalogItemDetail detail = new ShopUseCase.CatalogItemDetail(
                "ESS-001", "Essência Maçã", "essencia", new BigDecimal("30.00"), true, List.of(), null, false,
                new BigDecimal("40.00"), true, "Descrição longa", "http://video.mp4",
                List.of("http://img1.png", "http://img2.png"));
        when(shopUseCase.getCatalogItem("ESS-001")).thenReturn(detail);

        mockMvc.perform(get("/shop/catalog/ESS-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalPrice").value(40.00))
                .andExpect(jsonPath("$.superPromo").value(true))
                .andExpect(jsonPath("$.description").value("Descrição longa"))
                .andExpect(jsonPath("$.videoUrl").value("http://video.mp4"))
                .andExpect(jsonPath("$.images[0]").value("http://img1.png"))
                .andExpect(jsonPath("$.images[1]").value("http://img2.png"));
    }

    @Test
    void getCatalogItem_notFound_returns_404() throws Exception {
        when(shopUseCase.getCatalogItem("GHOST")).thenThrow(new ProductNotFoundException("GHOST"));

        mockMvc.perform(get("/shop/catalog/GHOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }


    @Test
    void listCategories_devolveNaOrdemDaVitrine() throws Exception {
        when(shopUseCase.listCategories()).thenReturn(List.of(
                Category.of(1L, "Promoções", true, 0, true),
                Category.of(2L, "Narguilé", false, 1, true)));

        mockMvc.perform(get("/shop/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Promoções"))
                .andExpect(jsonPath("$[0].featured").value(true))
                .andExpect(jsonPath("$[1].name").value("Narguilé"))
                .andExpect(jsonPath("$[1].displayOrder").value(1));
    }

    @Test
    void listCatalog_repassaCategoryIdAoUseCase() throws Exception {
        when(shopUseCase.listCatalog(0, 20, null, 7L, null))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/shop/catalog").param("categoryId", "7"))
                .andExpect(status().isOk());

        verify(shopUseCase).listCatalog(0, 20, null, 7L, null);
    }
}
