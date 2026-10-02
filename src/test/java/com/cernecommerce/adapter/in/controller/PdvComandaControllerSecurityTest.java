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

/**
 * PDV-F009: mesmo padrão de {@code PdvControllerSecurityTest} — sessão/comanda inexistente
 * (999999) é suficiente para exercitar 403/404 sem precisar montar um ciclo de caixa real.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PdvComandaControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String OPEN_BODY = "{\"tableOrCustomerLabel\":\"Mesa 4\"}";
    private static final String ADD_ITEM_BODY = "{\"sku\":\"NARG-001\",\"quantity\":1}";
    private static final String CLOSE_BODY = "{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":1}]}";

    // ── Abrir comanda ────────────────────────────────────────────────────────────────────────

    @Test
    void open_comanda_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/comandas?sessionId=999999")
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void open_comanda_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas?sessionId=999999")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void open_comanda_with_permission_and_nonexistent_session_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas?sessionId=999999")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CASH_REGISTER_SESSION_NOT_FOUND"));
    }

    // ── Lançar item ──────────────────────────────────────────────────────────────────────────

    @Test
    void add_item_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/items")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_ITEM_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void add_item_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/items")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_ITEM_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_item_with_permission_and_nonexistent_comanda_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/items")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_ITEM_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    // ── Consulta ─────────────────────────────────────────────────────────────────────────────

    @Test
    void get_comanda_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/comandas/999999")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_comanda_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/comandas/999999")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_nonexistent_comanda_with_pdv_read_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/comandas/999999")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    @Test
    void list_open_comandas_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/comandas?sessionId=999999")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_open_comandas_with_pdv_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/comandas?sessionId=999999")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk());
    }

    /** PDV-C007 — sem sessionId a rota passou a ser válida: são as mesas da loja. */
    @Test
    void list_open_comandas_without_sessionId_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/comandas")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    /**
     * PDV-C012 — o teto tem que ser aplicado de verdade, e é aqui que isso se prova: sem
     * {@code @Validated} na classe (ausente até esta correção) as constraints de parâmetro de
     * query são ignoradas em silêncio e a resposta seria 200.
     *
     * <p>Desde o Spring Framework 6.1 a violação chega como {@code HandlerMethodValidationException},
     * não {@code ConstraintViolationException} — ver EST-C005, onde a falta desse handler fazia
     * {@code GET /compras/suppliers?size=200} responder 500. O teste passa pela cadeia real de
     * propósito: o setup standalone não monta esse caminho.</p>
     */
    @Test
    void list_open_comandas_with_size_above_the_cap_returns_400() throws Exception {
        mockMvc.perform(get("/pdv/comandas?size=200")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void list_open_comandas_with_negative_page_returns_400() throws Exception {
        mockMvc.perform(get("/pdv/comandas?page=-1")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /**
     * PDV-C011 — o rótulo é {@code VARCHAR(100)} (V104) e tinha só {@code @NotBlank}: mais que isso
     * atravessava a validação e estourava no banco como 500, quando o certo é 400.
     */
    @Test
    void open_comanda_with_an_oversized_label_returns_400_not_500() throws Exception {
        String rotuloLongo = "M".repeat(101);
        mockMvc.perform(post("/pdv/comandas?sessionId=999999")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"" + rotuloLongo + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Encerrar (PDV-F023) ──────────────────────────────────────────────────────────────────

    @Test
    void finish_comanda_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/finish")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void finish_nonexistent_comanda_with_permission_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/finish")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    // ── Fechar ───────────────────────────────────────────────────────────────────────────────

    @Test
    void close_comanda_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void close_comanda_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_nonexistent_comanda_with_permission_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    /**
     * PDV-F014 — desconto no fechamento exige PDV_COMANDA_DISCOUNT, checado ANTES de qualquer
     * lookup: por isso a comanda inexistente responde 403 e não 404. Mesmo padrão da cortesia.
     */
    @Test
    void close_comanda_with_discount_without_the_discount_authority_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .with(user("atendente").authorities(
                                new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":90.00}],"
                                + "\"discountAmount\":10.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_DISCOUNT_NOT_ALLOWED"));
    }

    /** Com a permissão, o desconto passa da barreira e o fluxo segue até o 404 da comanda. */
    @Test
    void close_comanda_with_discount_and_the_authority_passes_the_permission_gate() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .with(user("gerente").authorities(
                                new SimpleGrantedAuthority("PDV_COMANDA_MANAGE"),
                                new SimpleGrantedAuthority("PDV_COMANDA_DISCOUNT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":90.00}],"
                                + "\"discountAmount\":10.00}"))
                .andExpect(status().isNotFound());
    }

    /** Fechamento sem desconto não pode exigir a permissão — seria 403 em toda mesa do salão. */
    @Test
    void close_comanda_without_discount_does_not_require_the_discount_authority() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .with(user("atendente").authorities(
                                new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY))
                .andExpect(status().isNotFound());
    }

    // ── Remover item (PDV-F012) ──────────────────────────────────────────────────────────────

    @Test
    void remove_item_without_auth_returns_401() throws Exception {
        mockMvc.perform(delete("/pdv/comandas/999999/items/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void remove_item_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/pdv/comandas/999999/items/1")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Reusa {@code PDV_COMANDA_MANAGE} de propósito: quem já pode cancelar a mesa inteira não
     * precisa de permissão maior para remover uma linha dela.
     */
    @Test
    void remove_item_with_pdv_comanda_manage_passes_the_permission_gate() throws Exception {
        mockMvc.perform(delete("/pdv/comandas/999999/items/1")
                        .with(user("atendente").authorities(
                                new SimpleGrantedAuthority("PDV_COMANDA_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    // ── Taxa de serviço (PDV-F015) ───────────────────────────────────────────────────────────

    @Test
    void get_service_fee_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/comandas/service-fee")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_service_fee_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/comandas/service-fee")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Também prova que a rota literal ganha de {@code GET /pdv/comandas/{id}}: sem essa
     * precedência, "service-fee" cairia na conversão para Long e viraria 400.
     */
    @Test
    void get_service_fee_with_pdv_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/comandas/service-fee")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").exists());
    }

    // ── Cancelar ─────────────────────────────────────────────────────────────────────────────

    @Test
    void cancel_comanda_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_comanda_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/cancel")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_nonexistent_comanda_with_permission_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/cancel")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    // ── Transferir e juntar mesas (PDV-F016) ─────────────────────────────────────────────────

    @Test
    void rename_comanda_without_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/pdv/comandas/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableOrCustomerLabel\":\"Mesa 7\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void merge_comanda_without_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/10/merge-into/20")
                .with(user("bob").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    // ── Histórico e indicadores (PDV-F029) ──────────────────────────────────────────────────

    private static final String PERIODO = "from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z";

    /** Vendas › Mesas é tela do relatório: quem só lê pedido (ORDER_READ) também entra. */
    @Test
    void history_with_order_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/comandas/history")
                        .with(user("dono").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void history_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/comandas/history")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void history_with_status_aberta_returns_400() throws Exception {
        mockMvc.perform(get("/pdv/comandas/history?status=ABERTA")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void history_with_size_above_the_cap_returns_400() throws Exception {
        mockMvc.perform(get("/pdv/comandas/history?size=101")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analytics_with_order_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/comandas/analytics?" + PERIODO)
                        .with(user("dono").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesas").isNumber())
                .andExpect(jsonPath("$.porMesa").isArray());
    }

    @Test
    void analytics_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/comandas/analytics?" + PERIODO)
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void analytics_without_period_returns_400() throws Exception {
        mockMvc.perform(get("/pdv/comandas/analytics")
                        .with(user("dono").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analytics_with_inverted_period_returns_400() throws Exception {
        mockMvc.perform(get("/pdv/comandas/analytics?from=2026-09-30T00:00:00Z&to=2026-09-01T00:00:00Z")
                        .with(user("dono").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isBadRequest());
    }

    /** O detalhe também abre para ORDER_READ: é de lá que o histórico leva ao pedido. */
    @Test
    void get_nonexistent_comanda_with_order_read_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/comandas/999999")
                        .with(user("dono").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    // ── PDV-F036 — "comprou na loja?" ────────────────────────────────────────────────────────

    @Test
    void store_purchase_without_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(put("/pdv/comandas/999999/store-purchase")
                        .with(user("dono").authorities(new SimpleGrantedAuthority("ORDER_READ")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"boughtInStore\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void store_purchase_on_nonexistent_comanda_returns_404() throws Exception {
        mockMvc.perform(put("/pdv/comandas/999999/store-purchase")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"boughtInStore\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    @Test
    void store_purchase_without_answer_returns_400() throws Exception {
        mockMvc.perform(put("/pdv/comandas/999999/store-purchase")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void history_filtered_by_store_purchase_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/comandas/history?boughtInStore=true")
                        .with(user("dono").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isOk());
    }
}
