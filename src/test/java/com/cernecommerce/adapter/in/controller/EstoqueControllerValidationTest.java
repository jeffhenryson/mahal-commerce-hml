package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Bean Validation nos parâmetros de query e de path do {@code EstoqueController} (EST-C005).
 *
 * <p><b>Por que com contexto real e não no standalone de {@code EstoqueControllerTest}:</b> desde
 * o Spring Framework 6.1 a validação de parâmetro de handler não passa por proxy AOP — quem a
 * aplica é o {@code RequestMappingHandlerAdapter}, usando o validador registrado no contexto MVC.
 * Um {@code standaloneSetup} não reproduz essa montagem de forma confiável, então o teto de
 * paginação e a validação de {@code sku}/{@code warehouseCode} só provam alguma coisa aqui.</p>
 *
 * <p>Cobre também a ponta de infra que faltava: a exceção lançada é
 * {@code HandlerMethodValidationException}, e não {@code ConstraintViolationException}. Sem o
 * handler correspondente no {@code GlobalExceptionHandler} ela cairia no catch-all e viraria 500.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class EstoqueControllerValidationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private MockHttpServletRequestBuilder asStockManager(MockHttpServletRequestBuilder request) {
        return request.with(user("gerente").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"),
                new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"),
                new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE")));
    }

    // ---------------------------------------------------------------------------------------
    // Paginação: o teto de 100 deixou de ser Math.min silencioso e passou a ser 400,
    // alinhando /estoque com /compras e /pdv.
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"/estoque/products", "/estoque/warehouses", "/estoque/integrity/orphan-skus"})
    void sizeAcimaDoTeto_returns_400(String path) throws Exception {
        mockMvc.perform(asStockManager(get(path).param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/estoque/products", "/estoque/warehouses", "/estoque/integrity/orphan-skus"})
    void sizeZero_returns_400(String path) throws Exception {
        mockMvc.perform(asStockManager(get(path).param("size", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/estoque/products", "/estoque/warehouses", "/estoque/integrity/orphan-skus"})
    void pageNegativa_returns_400(String path) throws Exception {
        mockMvc.perform(asStockManager(get(path).param("page", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /** O limite é inclusivo: 100 é válido, 101 não. */
    @ParameterizedTest
    @ValueSource(strings = {"/estoque/products", "/estoque/warehouses", "/estoque/integrity/orphan-skus"})
    void sizeExatamenteNoTeto_returns_200(String path) throws Exception {
        mockMvc.perform(asStockManager(get(path).param("page", "0").param("size", "100")))
                .andExpect(status().isOk());
    }

    @Test
    void listMovements_sizeAcimaDoTeto_returns_400() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/movements")
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "LOJA-01")
                        .param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ---------------------------------------------------------------------------------------
    // sku e warehouseCode: era o furo nominal do EST-C005 — chegavam sem nenhuma validação.
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "'',      LOJA-01",   // sku em branco
            "'  ',    LOJA-01",   // sku só com espaço
            "AB,      LOJA-01",   // sku abaixo do mínimo de 3
            "NARG-001, ''",       // warehouseCode em branco
            "NARG-001, L"         // warehouseCode abaixo do mínimo de 2
    })
    void getStockBalance_comParametroInvalido_returns_400(String sku, String warehouseCode) throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/stock-balance")
                        .param("sku", sku)
                        .param("warehouseCode", warehouseCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void getStockBalance_comSkuAcimaDe50Caracteres_returns_400() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/stock-balance")
                        .param("sku", "S".repeat(51))
                        .param("warehouseCode", "LOJA-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void listMovements_comQDeUmCaractere_returns_400() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/movements").param("q", "m")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void listMovements_comQAcimaDe80Caracteres_returns_400() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/movements").param("q", "m".repeat(81))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /** No limite (2 caracteres) passa da validação e chega à busca — 200 com a página, mesmo vazia. */
    @Test
    void listMovements_comQDeDoisCaracteres_passaDaValidacao() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/movements").param("q", "mz")))
                .andExpect(status().isOk());
    }

    @Test
    void listMovements_comSkuEmBranco_returns_400() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/movements")
                        .param("sku", "  ")
                        .param("warehouseCode", "LOJA-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /**
     * {@code sku} e {@code warehouseCode} são opcionais em {@code /estoque/movements} — alimentam
     * o feed geral de movimentações. Omitidos, a requisição passa direto da validação para a
     * regra de negócio: como "LOJA-01" não existe neste teste, o resultado é 404
     * WAREHOUSE_NOT_FOUND, não mais 400 MISSING_PARAMETER.
     */
    @Test
    void listMovements_semSku_naoCaiEmValidacao() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/movements").param("warehouseCode", "LOJA-01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    /** Sem nenhum filtro, alimenta o feed geral de movimentações — não é mais 400. */
    @Test
    void listMovements_semNenhumFiltro_returns_200() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/movements")))
                .andExpect(status().isOk());
    }

    /** Só {@code sku}, sem {@code warehouseCode}: também válido, não depende de depósito existir. */
    @Test
    void listMovements_semWarehouseCode_returns_200() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/movements").param("sku", "NARG-001")))
                .andExpect(status().isOk());
    }

    /** {@code sku} opcional em {@code /estoque/stock-balance}: omitido, também passa da validação. */
    @Test
    void getStockBalance_semSku_naoCaiEmValidacao() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/stock-balance")
                        .param("warehouseCode", "DEPOSITO-INEXISTENTE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    /** Validação roda antes de o depósito ser resolvido: não é 404. */
    @Test
    void getStockBalance_comParametrosValidos_naoCaiEmValidacao() throws Exception {
        mockMvc.perform(asStockManager(get("/estoque/stock-balance")
                        .param("sku", "NARG-001")
                        .param("warehouseCode", "DEPOSITO-INEXISTENTE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }
}
