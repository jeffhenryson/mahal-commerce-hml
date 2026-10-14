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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

@SpringBootTest
@ActiveProfiles("dev")
public class PdvControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_sessions_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_sessions_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sessions")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_sessions_with_pdv_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/sessions")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk());
    }

    /**
     * PDV-F004: sem {@code unitPrice} — o preço é resolvido pelo servidor.
     * PDV-C004: sem {@code warehouseCode} — o depósito vem da sessão de caixa.
     * PDV-F006: {@code payments} é obrigatório; o valor aqui não precisa bater com o preço real —
     * a sessão inexistente (999999) barra antes de qualquer validação de pagamento ser alcançada.
     */
    private static final String SALE_BODY = "{\"items\":[{\"sku\":\"NARG-001\",\"quantity\":1}],"
            + "\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":1}]}";

    /** Mesma venda, com desconto — exige a permissão PDV_SALE_DISCOUNT além de PDV_SALE_MANAGE. */
    private static final String SALE_BODY_WITH_DISCOUNT =
            "{\"items\":[{\"sku\":\"NARG-001\",\"quantity\":1,\"discountAmount\":1.00}],"
            + "\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":1}]}";

    private static final String OPEN_SESSION_BODY =
            "{\"openingAmount\":200.00,\"warehouseCode\":\"LOJA-01\"}";

    private static final String MOVEMENT_BODY =
            "{\"type\":\"SANGRIA\",\"amount\":50.00,\"reason\":\"cofre\"}";

    private static final String CLOSE_BODY = "{\"countedAmount\":495.00}";

    /**
     * PDV-C015 — a liquidação passou a exigir corpo. O valor não importa em nenhum destes
     * testes: {@code @Valid @RequestBody} é resolvido ANTES do {@code @PreAuthorize} (o
     * argumento é desserializado na resolução de argumentos, o interceptor de segurança roda na
     * invocação), então sem corpo o 403/404 que cada teste espera viraria 400.
     */
    private static final String SETTLE_BODY =
            "{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":10.00}]}";

    @Test
    void register_sale_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_sale_without_pdv_sale_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY)
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_sale_with_pdv_sale_manage_and_nonexistent_session_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    /**
     * PDV-F004: vender e conceder desconto não são a mesma autorização. Registrar venda é operação
     * de caixa; abater valor é decisão comercial.
     */
    @Test
    void register_sale_with_discount_but_without_pdv_sale_discount_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY_WITH_DISCOUNT)
                .with(user("caixa").authorities(
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Com a permissão de desconto, o pedido passa da checagem de autorização e só então esbarra na
     * sessão inexistente — provando que o 403 acima veio do desconto, não da rota.
     */
    @Test
    void register_sale_with_discount_and_pdv_sale_discount_reaches_the_service() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY_WITH_DISCOUNT)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"),
                        new SimpleGrantedAuthority("PDV_SALE_DISCOUNT"))))
                .andExpect(status().isNotFound());
    }

    // ── Ciclo de caixa (PDV-F001/F002) ───────────────────────────────────────────────────────

    @Test
    void open_session_without_pdv_session_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OPEN_SESSION_BODY)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void open_session_with_unknown_warehouse_returns_404() throws Exception {
        // Passou da autorização e esbarrou no depósito — prova que o 403 acima veio da permissão.
        mockMvc.perform(post("/pdv/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingAmount\":200.00,\"warehouseCode\":\"NAO-EXISTE\"}")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void open_session_without_opening_amount_returns_400() throws Exception {
        mockMvc.perform(post("/pdv/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\"}")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void current_session_without_open_register_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/sessions/current")
                .with(user("sem-caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void register_movement_without_pdv_session_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOVEMENT_BODY)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_movement_without_reason_returns_400() throws Exception {
        // Sangria sem motivo é indistinguível de desvio — a validação é de negócio, não estética.
        mockMvc.perform(post("/pdv/sessions/999999/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SANGRIA\",\"amount\":50.00}")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void close_session_requires_its_own_permission() throws Exception {
        // PDV_SESSION_MANAGE abre e movimenta, mas não fecha: a conferência é do gerente.
        mockMvc.perform(post("/pdv/sessions/999999/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CLOSE_BODY)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_nonexistent_session_with_permission_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CLOSE_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_SESSION_CLOSE"))))
                .andExpect(status().isNotFound());
    }

    // ── Liquidar no balcão pedido feito no app ───────────────────────────────────────────────

    @Test
    void pending_online_orders_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/pending-online-orders")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pending_online_orders_with_pdv_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/pending-online-orders")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void settle_online_order_without_pdv_sale_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/orders/7/settle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTLE_BODY)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void settle_nonexistent_session_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/orders/7/settle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTLE_BODY)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_order_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sales/1")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_nonexistent_order_with_pdv_read_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/sales/999999")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_session_orders_with_nonexistent_session_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/sessions/999999/sales")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }

    // ── PDV-F006: pagamento ──────────────────────────────────────────────────────────────────

    @Test
    void register_sale_without_payments_returns_400() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"sku\":\"NARG-001\",\"quantity\":1}]}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_receipt_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sales/1/receipt")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_receipt_for_nonexistent_order_with_pdv_read_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/sales/999999/receipt")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void payment_totals_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sessions/1/payment-totals")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void payment_totals_for_nonexistent_session_with_pdv_read_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/sessions/999999/payment-totals")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }

    // ── GET /pdv/sessions/{id} e /pdv/sessions/{id}/movements ──────────────────────────────────
    //
    // Auditoria de cobertura de testes de segurança (2026-08): nenhum dos dois tinha um único
    // teste, nem de 401/403 nem de 2xx. Também é aqui que mora o "IDOR" deste sistema — que é
    // single-tenant, sem isolamento multi-loja, só isolamento por dono do recurso: a sessão de
    // caixa pertence a quem a abriu. Ver a seção de propriedade horizontal mais abaixo.

    /**
     * Cadastra um depósito com código único e o devolve — pré-requisito para abrir sessão de
     * caixa e para estocar produto. Reaproveitado por {@link #givenOpenSession} (via chamador) e
     * por {@link #givenStockedSessionReadyForSale}.
     */
    private String givenWarehouse() throws Exception {
        String code = "LOJA_PDV_SEC_" + System.nanoTime();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente-setup").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());
        return code;
    }

    /** Abre uma sessão de caixa de verdade para {@code operator} no depósito informado, e devolve o id. */
    private Long givenOpenSession(String operator, String warehouseCode) throws Exception {
        String location = mockMvc.perform(post("/pdv/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingAmount\":200.00,\"warehouseCode\":\"" + warehouseCode + "\"}")
                .with(user(operator).authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void get_session_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/sessions/999999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_session_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sessions/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_session_with_pdv_read_returns_200() throws Exception {
        String warehouseCode = givenWarehouse();
        Long sessionId = givenOpenSession("operador-get-session", warehouseCode);

        mockMvc.perform(get("/pdv/sessions/" + sessionId)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.warehouseCode").value(warehouseCode));
    }

    @Test
    void list_cash_movements_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/sessions/999999/movements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_cash_movements_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sessions/999999/movements")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** PDV-C012 — a rota devolvia a List na raiz e passou a devolver PageResult. */
    @Test
    void list_cash_movements_with_pdv_read_returns_200() throws Exception {
        String warehouseCode = givenWarehouse();
        Long sessionId = givenOpenSession("operador-list-movements", warehouseCode);

        mockMvc.perform(get("/pdv/sessions/" + sessionId + "/movements")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    /** PDV-C012 — o teto vale aqui como já valia em GET /pdv/sessions. */
    @Test
    void list_cash_movements_with_size_above_the_cap_returns_400() throws Exception {
        String warehouseCode = givenWarehouse();
        Long sessionId = givenOpenSession("operador-movements-cap", warehouseCode);

        mockMvc.perform(get("/pdv/sessions/" + sessionId + "/movements?size=200")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ── Venda completa de ponta a ponta, propriedade horizontal e mass assignment ──────────────
    //
    // Até aqui, nenhum teste deste arquivo completava uma venda de verdade: todos usavam a sessão
    // 999999 (inexistente) e paravam em 403/404 antes de qualquer regra de negócio ser exercitada.
    // A partir daqui a receita monta depósito + produto precificado + estoque + sessão real, o
    // suficiente para um POST /pdv/sessions/{id}/sales chegar a 201 de verdade.

    /** SKU e preço nascem do catálogo — é o próprio ponto que PDV-F004 defende (preço não vem do cliente). */
    private record SaleFixture(Long sessionId, String sku, BigDecimal price) {
    }

    /**
     * Depósito com produto precificado e estocado, mais uma sessão de caixa aberta por
     * {@code operator} nesse depósito — pronta para registrar uma venda de verdade.
     */
    private SaleFixture givenStockedSessionReadyForSale(String operator) throws Exception {
        String warehouseCode = givenWarehouse();
        String sku = "SKU_PDV_SEC_" + System.nanoTime();
        BigDecimal price = new BigDecimal("50.00");

        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto PDV Teste\","
                        + "\"pricing\":{\"salePrice\":" + price + "}}")
                .with(user("gerente-setup").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_PRICE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + warehouseCode + "\","
                        + "\"type\":\"ENTRADA\",\"quantity\":10,\"reason\":\"setup teste\"}")
                .with(user("gerente-setup").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isCreated());

        Long sessionId = givenOpenSession(operator, warehouseCode);
        return new SaleFixture(sessionId, sku, price);
    }

    @Test
    void register_sale_with_pdv_sale_manage_returns_201() throws Exception {
        SaleFixture fixture = givenStockedSessionReadyForSale("operador-venda-ok");
        String body = "{\"items\":[{\"sku\":\"" + fixture.sku() + "\",\"quantity\":1}],"
                + "\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":" + fixture.price() + "}]}";

        mockMvc.perform(post("/pdv/sessions/" + fixture.sessionId() + "/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("operador-venda-ok").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grossAmount").value(fixture.price().doubleValue()));
    }

    /**
     * PDV-C004: o operador que abriu a sessão é o único que pode vender nela — a mesma regra que
     * {@code PdvServiceTest.registerSale_refusesASessionThatBelongsToAnotherOperator} prova em
     * nível de serviço, mas que nunca tinha sido exercitada na camada HTTP. Um bug de mapeamento
     * entre o {@code Authentication} e o {@code username} passado ao service passaria batido por
     * todos os testes de unidade e só apareceria aqui.
     *
     * <p>{@code CashRegisterSessionNotOwnedException} é mapeada pelo {@code GlobalExceptionHandler}
     * para 403/SESSION_NOT_OWNED (não 404): a sessão existe e o chamador sabe disso, só não é dele.</p>
     */
    @Test
    void register_sale_on_session_owned_by_another_operator_returns_403() throws Exception {
        SaleFixture fixture = givenStockedSessionReadyForSale("operadorA");
        String body = "{\"items\":[{\"sku\":\"" + fixture.sku() + "\",\"quantity\":1}],"
                + "\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":" + fixture.price() + "}]}";

        mockMvc.perform(post("/pdv/sessions/" + fixture.sessionId() + "/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("operadorB").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_OWNED"));
    }

    /**
     * Mesma regra de {@link #register_sale_on_session_owned_by_another_operator_returns_403}, mas
     * para a liquidação de pedido online. {@code PdvService.settleOnlineOrder} chama
     * {@code requireOwnOpenSession} ANTES de buscar o pedido — por isso um {@code orderId} fake
     * (999999) já basta: a checagem de dono nunca chega a olhar se o pedido existe. Confirmado lendo
     * {@code PdvService.settleOnlineOrder} (a chamada a {@code requireOwnOpenSession} precede
     * {@code getOrder(orderId)}) e {@code PdvServiceTest.settleOnlineOrder_refusesASessionThatBelongsToAnotherOperator}.
     */
    @Test
    void settle_online_order_on_session_owned_by_another_operator_returns_403() throws Exception {
        String warehouseCode = givenWarehouse();
        Long sessionId = givenOpenSession("operadorC", warehouseCode);

        mockMvc.perform(post("/pdv/sessions/" + sessionId + "/orders/999999/settle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTLE_BODY)
                .with(user("operadorD").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_OWNED"));
    }

    /**
     * PDV-F004: o comentário sobre {@code unitPrice} em {@link #SALE_BODY} nunca foi provado por
     * asserção nenhuma — a sessão 999999 barrava antes de qualquer preço ser resolvido.
     * {@code SaleItemRequest} não tem NENHUM campo de preço (ver a classe): "unitPrice" é
     * desconhecido do DTO, e o Jackson do Spring Boot ignora campos desconhecidos por padrão. O
     * valor malicioso de R$1,00 nunca chega ao service — o servidor usa o preço do catálogo
     * (R$50,00), provado aqui pelo total da resposta.
     */
    @Test
    void register_sale_with_client_supplied_price_is_ignored_and_server_price_is_used() throws Exception {
        SaleFixture fixture = givenStockedSessionReadyForSale("operador-mass-assignment");
        String body = "{\"items\":[{\"sku\":\"" + fixture.sku() + "\",\"quantity\":1,\"unitPrice\":1.00}],"
                + "\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":" + fixture.price() + "}]}";

        mockMvc.perform(post("/pdv/sessions/" + fixture.sessionId() + "/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user("operador-mass-assignment").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grossAmount").value(fixture.price().doubleValue()));
    }

    // ── PDV-F047: atividades da sessão de caixa ──────────────────────────────────────────────

    @Test
    void registrarAtividade_semPdvSaleManage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VARRER_LOJA\"}")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrarAtividade_comPdvSaleManage_chegaAoServico() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VARRER_LOJA\"}")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrarAtividade_outroSemNota_returns_400() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OUTRO\"}")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarAtividades_aceitaOrderRead() throws Exception {
        mockMvc.perform(get("/pdv/sessions/999999/activities")
                        .with(user("ana").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/pdv/sessions/999999/activities")
                        .with(user("ana").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }
}
