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
 * Segurança do módulo de cashback (CRM-F003): CASHBACK_RATE_MANAGE para mutar taxas,
 * CASHBACK_READ para todo o resto. Molde em {@code CrmControllerSecurityTest}.
 */
@SpringBootTest
@ActiveProfiles("dev")
public class CashbackControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ── V186 — parâmetros e prévia de resgate ───────────────────────────────────────────────

    @Test
    void get_settings_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/cashback/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    void put_settings_without_cashback_rate_manage_returns_403() throws Exception {
        mockMvc.perform(put("/cashback/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carenciaDias\":7,\"expiracaoDias\":180,\"resgateTetoPercent\":50,\"resgateMinimoSaldo\":50}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void redemption_quote_without_pdv_or_cashback_read_returns_403() throws Exception {
        mockMvc.perform(get("/cashback/customers/1/redemption-quote").param("amount", "100")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_rate_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/cashback/rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"GLOBAL\",\"percent\":5.0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_rate_without_cashback_rate_manage_returns_403() throws Exception {
        mockMvc.perform(post("/cashback/rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CATEGORY\",\"scopeRef\":\"narguile\",\"percent\":5.0}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_rate_with_cashback_rate_manage_returns_201() throws Exception {
        String category = "SEC_TEST_" + System.nanoTime();
        mockMvc.perform(post("/cashback/rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CATEGORY\",\"scopeRef\":\"" + category + "\",\"percent\":6.5}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("CASHBACK_RATE_MANAGE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("CATEGORY"))
                .andExpect(jsonPath("$.percent").value(6.5));
    }

    @Test
    void create_rate_with_invalid_scope_returns_400() throws Exception {
        mockMvc.perform(post("/cashback/rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"NAO_EXISTE\",\"percent\":5.0}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("CASHBACK_RATE_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_rates_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/cashback/rates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_rates_without_cashback_read_returns_403() throws Exception {
        mockMvc.perform(get("/cashback/rates")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_rates_with_cashback_read_returns_200() throws Exception {
        mockMvc.perform(get("/cashback/rates")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void resolve_rate_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/cashback/rates/resolve").param("sku", "CARV-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolve_rate_without_cashback_read_returns_403() throws Exception {
        mockMvc.perform(get("/cashback/rates/resolve").param("sku", "CARV-001")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolve_rate_for_unknown_sku_returns_404() throws Exception {
        mockMvc.perform(get("/cashback/rates/resolve").param("sku", "SKU-INEXISTENTE-XYZ")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void margin_impact_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/cashback/margin-impact").param("maxShare", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void margin_impact_without_max_share_returns_400() throws Exception {
        mockMvc.perform(get("/cashback/margin-impact")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void margin_impact_without_cashback_read_returns_403() throws Exception {
        mockMvc.perform(get("/cashback/margin-impact").param("maxShare", "20")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void margin_impact_with_cashback_read_returns_200() throws Exception {
        mockMvc.perform(get("/cashback/margin-impact").param("maxShare", "20")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void customer_balance_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/cashback/customers/999999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customer_balance_without_cashback_read_returns_403() throws Exception {
        mockMvc.perform(get("/cashback/customers/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_balance_for_unknown_customer_returns_404() throws Exception {
        mockMvc.perform(get("/cashback/customers/999999")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void customer_entries_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/cashback/customers/999999/entries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customer_entries_without_cashback_read_returns_403() throws Exception {
        mockMvc.perform(get("/cashback/customers/999999/entries")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_entries_with_cashback_read_returns_200() throws Exception {
        Long customerId = givenCustomer();
        mockMvc.perform(get("/cashback/customers/" + customerId + "/entries")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void patch_rate_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/cashback/rates/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percent\":4.0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_rate_without_cashback_rate_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/cashback/rates/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percent\":4.0}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("CASHBACK_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_unknown_rate_with_cashback_rate_manage_returns_404() throws Exception {
        mockMvc.perform(patch("/cashback/rates/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percent\":4.0}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("CASHBACK_RATE_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CASHBACK_RATE_NOT_FOUND"));
    }

    @Test
    void patch_rate_with_cashback_rate_manage_returns_200() throws Exception {
        Long rateId = givenRate();
        mockMvc.perform(patch("/cashback/rates/" + rateId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percent\":4.0}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("CASHBACK_RATE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(4.0));
    }

    /**
     * Cadastra uma taxa de cashback única (escopo CATEGORY) e devolve o id — fixture reaproveitada
     * pelos testes de sucesso que dependem de uma taxa existente.
     */
    private Long givenRate() throws Exception {
        String category = "SEC_TEST_" + System.nanoTime();
        String location = mockMvc.perform(post("/cashback/rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CATEGORY\",\"scopeRef\":\"" + category + "\",\"percent\":6.5}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("CASHBACK_RATE_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    /**
     * Cadastra um cliente único e devolve o id — fixture reaproveitada pelos testes de sucesso
     * que dependem de um cliente existente.
     */
    private Long givenCustomer() throws Exception {
        String email = "sec_test_" + System.nanoTime() + "@example.com";
        String location = mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"11999998888\",\"email\":\"" + email + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }
}
