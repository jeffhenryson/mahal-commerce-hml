package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("dev")
public class EstoqueControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * Cadastra um SKU único e o devolve. Desde EST-C002 movimentar saldo ou definir ponto de
     * reposição exige que o SKU exista no catálogo, então os testes de escrita precisam criar o
     * produto antes — só checar a authority não basta mais para chegar ao 2xx.
     */
    private String givenProduct() throws Exception {
        String sku = "SKU_SEC_TEST_" + System.nanoTime();
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto Teste\",\"category\":\"testes\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isCreated());
        return sku;
    }

    @Test
    void list_products_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_products_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/products")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_products_with_estoque_product_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/products")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void get_summary_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/summary"))
                .andExpect(status().isUnauthorized());
    }

    /** Exige AS DUAS permissões — só ESTOQUE_PRODUCT_READ não basta (bloco blenda catálogo e saldo). */
    @Test
    void get_summary_with_only_product_read_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/summary")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_summary_with_only_warehouse_read_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/summary")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_summary_with_both_permissions_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/summary")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_product_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_product_without_estoque_product_manage_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_product_with_estoque_product_manage_returns_201() throws Exception {
        String sku = "NARG_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Narguile Aladin\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void list_warehouses_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/warehouses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_warehouses_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/warehouses")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_warehouses_with_estoque_warehouse_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/warehouses")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_warehouse_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"LOJA-SEC\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_warehouse_without_estoque_warehouse_manage_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"LOJA-SEC\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_warehouse_with_estoque_warehouse_manage_returns_201() throws Exception {
        String code = "LOJA_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void get_stock_balance_with_estoque_warehouse_read_returns_200_or_404() throws Exception {
        // Sem depósito prévio cadastrado com esse código, a resposta é 404 (WAREHOUSE_NOT_FOUND) —
        // o importante aqui é a authority ser aceita (200/404), não bloqueada por 401/403.
        mockMvc.perform(get("/estoque/stock-balance")
                .param("sku", "NARG-001")
                .param("warehouseCode", "INEXISTENTE_SEC_TEST")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isNotFound());
    }

    /** Fluxo completo: produto + depósito + movimento de ENTRADA, e o saldo consultado bate com o movimentado. */
    @Test
    void get_stock_balance_with_estoque_warehouse_read_returns_200() throws Exception {
        String sku = givenProduct();
        String code = "LOJA_BALANCE_SEC_" + System.nanoTime();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + code + "\",\"type\":\"ENTRADA\","
                        + "\"quantity\":7,\"reason\":\"teste\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/estoque/stock-balance")
                .param("sku", sku)
                .param("warehouseCode", code)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.warehouseCode").value(code))
                .andExpect(jsonPath("$.quantity").value(7.0));
    }

    @Test
    void get_stock_balance_without_estoque_warehouse_read_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/stock-balance")
                .param("sku", "NARG-001")
                .param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_movement_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                        + "\"quantity\":1,\"reason\":\"teste\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_movement_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                        + "\"quantity\":1,\"reason\":\"teste\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_movement_with_estoque_stock_manage_returns_201() throws Exception {
        String sku = givenProduct();
        String code = "LOJA_MOV_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + code + "\",\"type\":\"ENTRADA\","
                        + "\"quantity\":1,\"reason\":\"teste\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseCode").value(code));
    }

    @Test
    void set_reorder_point_without_auth_returns_401() throws Exception {
        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\",\"minQuantity\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void set_reorder_point_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\",\"minQuantity\":10}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void set_reorder_point_with_estoque_stock_manage_returns_204() throws Exception {
        String sku = givenProduct();
        String code = "LOJA_REORDER_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/estoque/products/" + sku + "/reorder-point")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"" + code + "\",\"minQuantity\":10}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void list_movements_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_movements_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /**
     * EST-C015 — depois que a leitura do ledger passou a aceitar {@code ESTOQUE_PRODUCT_READ}, este
     * caso continua 403, e continua certo: {@code WAREHOUSE_READ} não é nenhuma das duas
     * autoridades aceitas. Quem enxerga depósito não enxerga, por tabela, o histórico de quem
     * movimentou o quê — para isso é preciso ler o catálogo ou movimentar saldo.
     */
    @Test
    void list_movements_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    /**
     * EST-C015 — o caso que a correção existe para permitir. {@code ESTOQUE_PRODUCT_READ} sozinho,
     * sem {@code STOCK_MANAGE}, é exatamente o recorte do {@code ROLE_ATENDENTE}
     * ({@code SeedConfig.ATENDENTE_PERMISSIONS}): quem opera o PDV e a mesa lê o ledger. Antes
     * disto o operador tomava 403 e o interceptor do admin o expulsava para /access-denied.
     */
    @Test
    void list_movements_with_product_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_movements_with_estoque_stock_manage_returns_200() throws Exception {
        String code = "LOJA_MOVLIST_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .param("warehouseCode", code)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    // EST-F018 — PATCH e desativação reusam as permissões de MANAGE do respectivo recurso.

    @Test
    void patch_product_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Ler o catálogo não dá direito de editá-lo. */
    @Test
    void patch_product_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_product_with_product_manage_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Produto Renomeado\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void patch_product_active_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_product_active_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_product_active_with_product_manage_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku + "/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void patch_product_lotTracked_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU/lot-tracked")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotTracked\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_product_lotTracked_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU/lot-tracked")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotTracked\":true}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_product_lotTracked_with_product_manage_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku + "/lot-tracked")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotTracked\":true}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void patch_warehouse_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/estoque/warehouses/QUALQUER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_warehouse_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/warehouses/QUALQUER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_warehouse_active_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/estoque/warehouses/QUALQUER/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());
    }

    /** Ver depósito não dá direito de ativar/desativar — é MANAGE, não READ, que autoriza. */
    @Test
    void patch_warehouse_active_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/warehouses/QUALQUER/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_warehouse_and_active_with_warehouse_manage_returns_200() throws Exception {
        String code = "LOJA_PATCH_SEC_" + System.nanoTime();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/estoque/warehouses/" + code)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Loja Renomeada\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/estoque/warehouses/" + code + "/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // EST-F006 — o balanço reusa ESTOQUE_STOCK_MANAGE: fechar uma contagem é movimentar saldo.

    @Test
    void open_stock_count_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"QUALQUER\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Ver saldo não dá direito de abrir balanço — fechar um aplica ajuste. */
    @Test
    void open_stock_count_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"QUALQUER\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_stock_count_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/stock-counts/1")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_stock_count_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/1/close"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void close_stock_count_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/1/close")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_stock_count_with_estoque_stock_manage_returns_200() throws Exception {
        StockCountFixture fixture = givenOpenStockCount();
        mockMvc.perform(post("/estoque/stock-counts/" + fixture.id() + "/close")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void open_stock_count_with_estoque_stock_manage_returns_201() throws Exception {
        String code = "LOJA_COUNT_SEC_" + System.nanoTime();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"" + code + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isCreated());
    }

    /**
     * Fixture completa: produto + depósito + balanço aberto, reaproveitada pelos testes de
     * sucesso de /stock-counts/{id}/items, /cancel, GET {id} e GET (listagem) — nenhum deles
     * tinha caminho 2xx coberto (auditoria de cobertura de testes de segurança, 2026-08).
     */
    private record StockCountFixture(Long id, String warehouseCode, String sku) {
    }

    private StockCountFixture givenOpenStockCount() throws Exception {
        String sku = givenProduct();
        String code = "LOJA_COUNT_SEC_" + System.nanoTime();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        String location = mockMvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"" + code + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        return new StockCountFixture(id, code, sku);
    }

    @Test
    void record_counted_item_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"QUALQUER\",\"countedQuantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void record_counted_item_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"QUALQUER\",\"countedQuantity\":1}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void record_counted_item_with_estoque_stock_manage_returns_200() throws Exception {
        StockCountFixture fixture = givenOpenStockCount();
        mockMvc.perform(post("/estoque/stock-counts/" + fixture.id() + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + fixture.sku() + "\",\"countedQuantity\":10}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_stock_count_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_stock_count_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/1/cancel")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_stock_count_with_estoque_stock_manage_returns_200() throws Exception {
        StockCountFixture fixture = givenOpenStockCount();
        mockMvc.perform(post("/estoque/stock-counts/" + fixture.id() + "/cancel")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void get_stock_count_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/stock-counts/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_stock_count_with_estoque_stock_manage_returns_200() throws Exception {
        StockCountFixture fixture = givenOpenStockCount();
        mockMvc.perform(get("/estoque/stock-counts/" + fixture.id())
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_stock_counts_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/stock-counts").param("warehouseCode", "QUALQUER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_stock_counts_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/stock-counts").param("warehouseCode", "QUALQUER")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_stock_counts_with_estoque_stock_manage_returns_200() throws Exception {
        StockCountFixture fixture = givenOpenStockCount();
        mockMvc.perform(get("/estoque/stock-counts").param("warehouseCode", fixture.warehouseCode())
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_orphan_skus_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/integrity/orphan-skus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_orphan_skus_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/integrity/orphan-skus")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /**
     * O diagnóstico de integridade expõe o passivo de dados sujos do estoque, então segue a mesma
     * régua do ledger: exige STOCK_MANAGE, e WAREHOUSE_READ não basta.
     */
    @Test
    void list_orphan_skus_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/integrity/orphan-skus")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_orphan_skus_with_estoque_stock_manage_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/integrity/orphan-skus")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------
    // EST-F013/EST-F021 — reserva de estoque (listagem) e EST-C013 (integridade).
    // ------------------------------------------------------------------------------------

    @Test
    void list_reservations_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_reservations_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/reservations")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Consultar disponível reservado não é a mesma authority de gerenciar estoque físico. */
    @Test
    void list_reservations_with_estoque_stock_manage_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/reservations")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_reservations_with_estoque_reservation_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/reservations")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_RESERVATION_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void get_reservation_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/reservations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_reservation_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/reservations/1")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Sem reserva cadastrada com esse id, a resposta é 404 — o que importa é a authority não barrar antes. */
    @Test
    void get_reservation_with_estoque_reservation_read_returns_404_whenNotFound() throws Exception {
        mockMvc.perform(get("/estoque/reservations/999999")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_RESERVATION_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void list_reservation_mismatches_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/integrity/reservation-mismatch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_reservation_mismatches_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/integrity/reservation-mismatch")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Mesma régua do EST-C011: diagnóstico de integridade exige STOCK_MANAGE, não RESERVATION_READ. */
    @Test
    void list_reservation_mismatches_with_reservation_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/integrity/reservation-mismatch")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_RESERVATION_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_reservation_mismatches_with_estoque_stock_manage_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/integrity/reservation-mismatch")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------
    // EST-F008 — lote e validade: listagem de lote e diagnóstico de integridade.
    // ------------------------------------------------------------------------------------

    @Test
    void list_stock_lots_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/products/QUALQUER-SKU/lots").param("warehouseCode", "LOJA-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_stock_lots_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/products/QUALQUER-SKU/lots").param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Mesma régua da receita de kit: leitura de catálogo, PRODUCT_READ basta. */
    @Test
    void list_stock_lots_with_product_read_returns_200() throws Exception {
        String code = "LOJA_LOT_SEC_" + System.nanoTime();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/estoque/products/QUALQUER-SKU/lots").param("warehouseCode", code)
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void list_lot_mismatches_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/integrity/lot-mismatch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_lot_mismatches_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/integrity/lot-mismatch")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Mesma régua dos outros diagnósticos: exige STOCK_MANAGE, não PRODUCT_READ. */
    @Test
    void list_lot_mismatches_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/integrity/lot-mismatch")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_lot_mismatches_with_estoque_stock_manage_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/integrity/lot-mismatch")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------
    // EST-F019 — precificação exige ESTOQUE_PRODUCT_PRICE_MANAGE além de PRODUCT_MANAGE.
    // A separação existe para que quem mantém o cadastro (nome, categoria, grade) não ganhe
    // de brinde o poder de mexer em preço, que é decisão comercial e não operacional.
    // ------------------------------------------------------------------------------------

    @Test
    void create_product_com_pricing_sem_price_manage_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU_PRICE_403\",\"name\":\"Narguile\","
                        + "\"pricing\":{\"costPrice\":45.00,\"markupPercent\":80}}")
                .with(user("operador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    /** Sem o bloco `pricing`, PRODUCT_MANAGE sozinho continua bastando — nada regrediu. */
    @Test
    void create_product_sem_pricing_com_product_manage_returns_201() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU_SEM_PRICE_" + System.nanoTime() + "\",\"name\":\"Narguile\"}")
                .with(user("operador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_product_com_pricing_e_price_manage_returns_201() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU_PRICE_OK_" + System.nanoTime() + "\",\"name\":\"Narguile\","
                        + "\"pricing\":{\"costPrice\":45.00,\"markupPercent\":80}}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_PRICE_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void patch_product_com_pricing_sem_price_manage_returns_403() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pricing\":{\"costPrice\":60.00}}")
                .with(user("operador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_product_sem_pricing_com_product_manage_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nome Novo\"}")
                .with(user("operador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------
    // Campos de marketing (originalPrice, superPromo, description, videoUrl, images):
    // originalPrice mora dentro do bloco pricing e segue a mesma regra de PRICE_MANAGE;
    // os demais moram no nível raiz do request e seguem a mesma regra de PRODUCT_MANAGE
    // já usada por brand/imageUrl/onSale — nenhuma permissão nova foi criada para eles.
    // ------------------------------------------------------------------------------------

    @Test
    void create_product_com_original_price_sem_price_manage_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU_ORIGPRICE_403\",\"name\":\"Narguile\","
                        + "\"pricing\":{\"originalPrice\":99.90}}")
                .with(user("operador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_product_com_super_promo_sem_product_manage_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU_SUPERPROMO_403\",\"name\":\"Narguile\",\"superPromo\":true}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_product_com_super_promo_description_video_images_com_apenas_product_manage_returns_201()
            throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU_MKT_OK_" + System.nanoTime() + "\",\"name\":\"Narguile\","
                        + "\"superPromo\":true,\"description\":\"Descrição\","
                        + "\"videoUrl\":\"http://video.mp4\",\"images\":[\"http://img1.png\"]}")
                .with(user("operador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void patch_product_com_original_price_sem_price_manage_returns_403() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pricing\":{\"originalPrice\":99.90}}")
                .with(user("operador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_product_com_super_promo_e_description_com_apenas_product_manage_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"superPromo\":true,\"description\":\"Nova descrição\"}")
                .with(user("operador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void get_product_price_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/products/QUALQUER-SKU/price"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_product_price_without_product_read_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/products/QUALQUER-SKU/price")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Consultar preço é leitura de catálogo: PRODUCT_READ basta, não exige a de precificar. */
    @Test
    void get_product_price_with_product_read_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(get("/estoque/products/" + sku + "/price")
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priced").value(false));
    }

    // ── Kits (EST-F015) ──────────────────────────────────────────────────────────────────────

    @Test
    void define_kit_recipe_without_auth_returns_401() throws Exception {
        mockMvc.perform(put("/estoque/products/QUALQUER-SKU/kit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"components\":[{\"componentSku\":\"CARV-001\",\"quantity\":1}]}"))
                .andExpect(status().isUnauthorized());
    }

    /** ESTOQUE_PRODUCT_MANAGE não autoriza: definir receita muda o que toda venda futura consome. */
    @Test
    void define_kit_recipe_with_product_manage_only_returns_403() throws Exception {
        mockMvc.perform(put("/estoque/products/QUALQUER-SKU/kit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"components\":[{\"componentSku\":\"CARV-001\",\"quantity\":1}]}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void define_kit_recipe_with_estoque_kit_manage_returns_200() throws Exception {
        String kitSku = givenProduct();
        String componentSku = "SKU_SEC_TEST_" + System.nanoTime();
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + componentSku + "\",\"name\":\"Componente Teste\",\"category\":\"testes\","
                        + "\"kitComponentEligible\":true}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/estoque/products/" + kitSku + "/kit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"components\":[{\"componentSku\":\"" + componentSku + "\",\"quantity\":2}]}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_KIT_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("KIT"));
    }

    @Test
    void get_kit_recipe_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/products/QUALQUER-SKU/kit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_kit_recipe_without_product_read_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/products/QUALQUER-SKU/kit")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Consultar a receita é leitura de catálogo: PRODUCT_READ basta, não exige a de gerenciar kit. */
    @Test
    void get_kit_recipe_with_product_read_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(get("/estoque/products/" + sku + "/kit")
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ===== Preço por variação: a permissão de preço não pode vazar (EST-F020) =====
    //
    // Antes de haver preço dentro de variants[], o @PreAuthorize checava só #request.pricing.
    // Estes testes travam o fechamento desse furo: quem tem apenas ESTOQUE_PRODUCT_MANAGE não
    // pode precificar por nenhum dos dois caminhos.

    @Test
    void create_product_com_pricing_na_variante_sem_price_manage_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku":"SEC_VAR_1","name":"Essência","variants":[
                          {"sku":"SEC_VAR_1_A","pricing":{"salePrice":99.90}}]}""")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_product_com_pricing_na_variante_com_price_manage_returns_201() throws Exception {
        String sku = "SEC_VAR_2_" + System.nanoTime();
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Essência\",\"variants\":["
                        + "{\"sku\":\"" + sku + "_A\",\"pricing\":{\"salePrice\":99.90}}]}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_PRICE_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_product_com_variante_sem_pricing_dispensa_price_manage() throws Exception {
        // O caminho comum — grade que herda o preço do pai — não pode ficar mais difícil.
        String sku = "SEC_VAR_3_" + System.nanoTime();
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Essência\",\"variants\":["
                        + "{\"sku\":\"" + sku + "_A\",\"attributes\":[{\"type\":\"Sabor\",\"value\":\"Menta\"}]}]}")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_product_com_pricing_na_segunda_variante_sem_price_manage_returns_403() throws Exception {
        // A checagem varre todas as variações, não só a primeira.
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku":"SEC_VAR_4","name":"Essência","variants":[
                          {"sku":"SEC_VAR_4_A"},
                          {"sku":"SEC_VAR_4_B","pricing":{"costPrice":10.00}}]}""")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    // ===== Busca por SKU e filtros da listagem =====

    @Test
    void get_product_by_sku_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/products/QUALQUER-SKU"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_product_by_sku_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/products/QUALQUER-SKU")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_product_by_sku_with_estoque_product_read_returns_200() throws Exception {
        String sku = givenProduct();
        mockMvc.perform(get("/estoque/products/" + sku)
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_products_com_filtros_exige_a_mesma_permissao_de_leitura() throws Exception {
        // Os filtros novos não podem virar uma porta lateral: continuam sob ESTOQUE_PRODUCT_READ.
        mockMvc.perform(get("/estoque/products")
                .param("search", "menta").param("active", "true").param("sort", "NAME")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/estoque/products")
                .param("search", "menta").param("active", "true").param("sort", "NAME")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    // ===== Upload de imagem de produto =====
    // Escrever no catálogo é operação de estoque, então o upload entra na matriz do módulo.
    // A leitura (GET /product-images/{filename}) é pública e não aparece aqui.

    private static MockMultipartFile jpegFile() {
        return new MockMultipartFile("file", "foto.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11});
    }

    @Test
    void upload_product_image_without_auth_returns_401() throws Exception {
        mockMvc.perform(multipart("/estoque/products/images").file(jpegFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_product_image_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(multipart("/estoque/products/images").file(jpegFile())
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_product_image_with_product_read_only_returns_403() throws Exception {
        // Ler o catálogo não dá direito de subir arquivo para o servidor.
        mockMvc.perform(multipart("/estoque/products/images").file(jpegFile())
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_product_image_with_estoque_product_manage_returns_200() throws Exception {
        mockMvc.perform(multipart("/estoque/products/images").file(jpegFile())
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void serve_product_image_is_public_and_returns_404_for_unknown_file() throws Exception {
        // Sem token: a vitrine do marketplace precisa renderizar a foto. Arquivo inexistente é
        // 404, não 401 — o que prova que a rota passou pelo filtro de segurança.
        mockMvc.perform(get("/product-images/inexistente.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serve_product_image_rejects_encoded_path_traversal_no_firewall() throws Exception {
        // O StrictHttpFirewall do Spring Security barra a barra codificada antes de chegar ao
        // controller — por isso 400 e não o 404 do guard. É a primeira das duas barreiras.
        mockMvc.perform(get("/product-images/..%2F..%2Fapplication.properties"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serve_product_image_rejects_dot_dot_no_controller() throws Exception {
        // Segunda barreira: nome que passa pelo firewall mas contém "..". Quem barra aqui é o
        // guard do ProductImageController, e o 404 prova que ele rodou sem tocar o storage.
        mockMvc.perform(get("/product-images/a..b.jpg"))
                .andExpect(status().isNotFound());
    }


    // ===== Categorias: ESTOQUE_CATEGORY_MANAGE para escrita, PRODUCT_READ para leitura =====

    private Long givenCategory() throws Exception {
        String body = mockMvc.perform(post("/estoque/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cat Sec " + System.nanoTime() + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_CATEGORY_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void list_categories_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_categories_with_estoque_product_read_returns_200() throws Exception {
        // Ler categoria é ler catálogo — não exige a permissão de gestão.
        mockMvc.perform(get("/estoque/categories")
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_category_with_product_manage_only_returns_403() throws Exception {
        // Manter o cadastro de produtos não dá direito de reorganizar a vitrine.
        mockMvc.perform(post("/estoque/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Proibida\"}")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_category_with_category_manage_returns_201() throws Exception {
        mockMvc.perform(post("/estoque/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cat Sec Ok " + System.nanoTime() + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_CATEGORY_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void patch_category_without_category_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featured\":true}")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_category_with_category_manage_returns_200() throws Exception {
        Long id = givenCategory();
        mockMvc.perform(patch("/estoque/categories/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featured\":true,\"displayOrder\":2}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_CATEGORY_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(true));
    }

    @Test
    void patch_category_active_without_category_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/categories/1/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_category_active_with_category_manage_returns_200() throws Exception {
        Long id = givenCategory();
        mockMvc.perform(patch("/estoque/categories/" + id + "/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_CATEGORY_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shop_categories_is_public() throws Exception {
        // Sem token: é a navegação da vitrine, lida pelo app antes de qualquer login.
        mockMvc.perform(get("/shop/categories")).andExpect(status().isOk());
    }

    // ===== Marcas: ESTOQUE_BRAND_MANAGE para escrita, PRODUCT_READ para leitura (item 3) =====

    @Test
    void list_brands_with_estoque_product_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/brands")
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_brand_with_product_manage_only_returns_403() throws Exception {
        // Manter o cadastro de produtos não dá direito de gerir o vocabulário de marcas.
        mockMvc.perform(post("/estoque/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Proibida\"}")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_brand_with_brand_manage_returns_201() throws Exception {
        mockMvc.perform(post("/estoque/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Marca Sec " + System.nanoTime() + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_BRAND_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_brand_without_brand_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/estoque/brands/1")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    // ===== Vocabulário de atributos: ESTOQUE_ATTRIBUTE_MANAGE (item 5) =====

    @Test
    void list_attribute_types_with_estoque_product_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/attribute-types")
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_attribute_type_with_product_manage_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/attribute-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Proibido\"}")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_attribute_type_with_attribute_manage_returns_201() throws Exception {
        mockMvc.perform(post("/estoque/attribute-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tipo Sec " + System.nanoTime() + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_ATTRIBUTE_MANAGE"))))
                .andExpect(status().isCreated());
    }

    // ===== Lista de Reposição: ESTOQUE_REPLENISHMENT_MANAGE para escrita (item 1) =====

    @Test
    void list_replenishment_items_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/replenishment-list").param("warehouseCode", "LOJA-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upsert_replenishment_item_with_product_manage_only_returns_403() throws Exception {
        // Manter o cadastro de produtos não dá direito de escrever na lista de reposição.
        mockMvc.perform(post("/estoque/replenishment-list/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"quantity\":1}")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void upsert_replenishment_item_with_replenishment_manage_returns_201() throws Exception {
        String sku = givenProduct();
        String code = "LOJA_REPL_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/replenishment-list/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + code + "\",\"quantity\":5}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_REPLENISHMENT_MANAGE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku));

        mockMvc.perform(get("/estoque/replenishment-list").param("warehouseCode", code)
                .with(user("vendedor").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value(sku));
    }

    @Test
    void clear_replenishment_list_without_replenishment_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/estoque/replenishment-list").param("warehouseCode", "LOJA-01")
                .with(user("cadastrador").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    // ── Conversão atômica entre SKUs (EST-F025) ──────────────────────────────────────────────

    /**
     * Converter altera saldo dos dois lados, então é escrita: exige {@code STOCK_MANAGE} como o
     * {@code POST /estoque/movements}, e não a permissão de leitura que EST-C015 liberou para o
     * histórico. Na prática significa que o {@code ROLE_ATENDENTE} lê o ledger mas não converte.
     */
    @Test
    void convert_stock_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/conversions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromSku\":\"ESS-LATA\",\"toSku\":\"SESS-BLUE\",\"fromQuantity\":1,"
                        + "\"toQuantity\":5,\"warehouseCode\":\"LOJA-01\",\"reason\":\"Fracionamento\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void convert_stock_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/conversions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromSku\":\"ESS-LATA\",\"toSku\":\"SESS-BLUE\",\"fromQuantity\":1,"
                        + "\"toQuantity\":5,\"warehouseCode\":\"LOJA-01\",\"reason\":\"Fracionamento\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Curva ABC (EST-F011) ─────────────────────────────────────────────────────────────────

    /** É leitura: a mesma régua que EST-C015 estabeleceu para o ledger. */
    @Test
    void abc_analysis_with_product_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/analytics/abc")
                .param("from", "2026-08-01T00:00:00Z")
                .param("to", "2026-08-31T23:59:59Z")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void abc_analysis_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/analytics/abc")
                .param("from", "2026-08-01T00:00:00Z")
                .param("to", "2026-08-31T23:59:59Z")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    // ── Lata aberta (EST-F027) ───────────────────────────────────────────────────────────────

    /**
     * Ler as latas é leitura de catálogo — mesma régua de EST-C015 para o ledger. O atendente
     * também alcança, porque é a tela de sessão dele que mostra o "3 de 5". O 404 é do depósito
     * que não existe na base de teste: o que este caso afirma é que o RBAC deixou passar.
     */
    @Test
    void open_packages_with_product_read_reaches_the_route() throws Exception {
        mockMvc.perform(get("/estoque/open-packages")
                .param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void open_packages_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/open-packages")
                .param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /**
     * "Repor essência" baixa estoque, mas quem repõe é o <b>atendente</b>, que tem
     * PDV_COMANDA_MANAGE e não ESTOQUE_STOCK_MANAGE. Exigir só a segunda tornaria o botão
     * inalcançável justamente para quem aperta.
     */
    @Test
    void replace_open_package_with_comanda_manage_reaches_the_route() throws Exception {
        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE/replace")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\"}")
                .with(user("atendente").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("PDV_COMANDA_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    /**
     * EST-F033 — mesma régua do {@code /replace}: quem cadastra as latas que já estão abertas na
     * bancada é o atendente da mesa. O 404 é do depósito ausente na base de teste.
     */
    @Test
    void register_open_package_with_comanda_manage_reaches_the_route() throws Exception {
        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\",\"usesRemaining\":2}")
                .with(user("atendente").authorities(
                        new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                        new SimpleGrantedAuthority("PDV_COMANDA_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    /** Só leitura de catálogo não cadastra lata: ela muda o que a próxima sessão consome. */
    @Test
    void register_open_package_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\",\"usesRemaining\":2}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void replace_open_package_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/open-packages/ESSE-BLUE/replace")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    // ── Produto base com variações (EST-F036) ────────────────────────────────────────────────

    @Test
    void set_parent_sellable_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/products/LM/parent-sellable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentSellable\":true}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    // ── Embalagem (EST-F032) ─────────────────────────────────────────────────────────────────

    /** Ligar embalagem muda o que toda venda futura faz sozinha: é cadastro de produto, não de caixa. */
    @Test
    void define_packaging_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(put("/estoque/products/LM-AZUL-UN/packaging")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentSku\":\"LM-AZUL-MACO\",\"unitsPerParent\":20}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    /** Com a permissão o RBAC deixa passar; o 404 é do SKU ausente na base de teste. */
    @Test
    void define_packaging_with_product_manage_reaches_the_route() throws Exception {
        mockMvc.perform(put("/estoque/products/LM-AZUL-UN/packaging")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentSku\":\"LM-AZUL-MACO\",\"unitsPerParent\":20}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void read_packaging_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/products/LM-AZUL-UN/packaging")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    // ── Exclusão de rascunho (EST-F026) ──────────────────────────────────────────────────────

    @Test
    void delete_product_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(delete("/estoque/products/SKU-DRAFT")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_product_without_auth_returns_401() throws Exception {
        mockMvc.perform(delete("/estoque/products/SKU-DRAFT"))
                .andExpect(status().isUnauthorized());
    }
}
