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

import java.util.UUID;

/** PDV-F021 — atendente lê o cardápio e lança; só o admin mexe no cadastro. */
@SpringBootTest
@ActiveProfiles("dev")
public class PdvSessaoControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private static final SimpleGrantedAuthority COMANDA = new SimpleGrantedAuthority("PDV_COMANDA_MANAGE");
    private static final SimpleGrantedAuthority SESSAO_MANAGE = new SimpleGrantedAuthority("PDV_SESSAO_MANAGE");

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ── PDV-F041: central de cigarros ──

    /** O atendente do balcão lê a central (PDV_READ); o 404 é do depósito ausente na base de teste. */
    @Test
    void cigarros_with_pdv_read_reaches_the_route() throws Exception {
        mockMvc.perform(get("/pdv/cigarros").param("warehouseCode", "LOJA-INEXISTENTE")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cigarros_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/cigarros").param("warehouseCode", "LOJA-01")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void menu_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/sessao/cardapio")).andExpect(status().isUnauthorized());
    }

    @Test
    void menu_with_comanda_manage_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/sessao/cardapio").with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faixas").isArray())
                .andExpect(jsonPath("$.duploRoshHoje").isBoolean());
    }

    /**
     * PDV-C025 — o 2º rosh do duplo é cortesia: sem PDV_COMANDA_COURTESY, 403 antes de qualquer
     * leitura da mesa (a comanda nem existe aqui). Com a permissão, passa da checagem e cai no 404.
     */
    @Test
    void session_duplo_without_courtesy_returns_403() throws Exception {
        String body = "{\"tierId\":1,\"essencia\":\"Zomo\",\"modo\":\"DUPLO\",\"essenciaRosh\":\"Pred\"}";
        mockMvc.perform(post("/pdv/comandas/987654321/sessoes").contentType(MediaType.APPLICATION_JSON)
                        .content(body).with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COURTESY_NOT_ALLOWED"));
        mockMvc.perform(post("/pdv/comandas/987654321/sessoes").contentType(MediaType.APPLICATION_JSON)
                        .content(body).with(user("gerente").authorities(COMANDA,
                                new SimpleGrantedAuthority("PDV_COMANDA_COURTESY"))))
                .andExpect(status().isNotFound());
    }

    /**
     * PDV-F034 — sessão paga no final sem PDV_SESSION_PAY_LATER: 403 antes de ler a mesa. Com a
     * permissão, passa da checagem e cai no 404 da comanda inexistente.
     */
    @Test
    void session_pay_later_without_permission_returns_403() throws Exception {
        String body = "{\"tierId\":1,\"essencia\":\"Zomo\",\"pagarNoFinal\":true}";
        mockMvc.perform(post("/pdv/comandas/987654321/sessoes").contentType(MediaType.APPLICATION_JSON)
                        .content(body).with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SESSION_PAY_LATER_NOT_ALLOWED"));
        mockMvc.perform(post("/pdv/comandas/987654321/sessoes").contentType(MediaType.APPLICATION_JSON)
                        .content(body).with(user("gerente").authorities(COMANDA,
                                new SimpleGrantedAuthority("PDV_SESSION_PAY_LATER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void repeat_session_pay_later_without_permission_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/987654321/sessoes/1/repetir").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagarNoFinal\":true}").with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SESSION_PAY_LATER_NOT_ALLOWED"));
    }

    /** PDV-C031 — duplo sem o sabor do 2º rosh tem código próprio, não o 400 genérico. */
    @Test
    void session_duplo_without_rosh_flavor_returns_session_essence_required() throws Exception {
        mockMvc.perform(post("/pdv/comandas/987654321/sessoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":1,\"essencia\":\"Zomo\",\"modo\":\"DUPLO\"}")
                        .with(user("gerente").authorities(COMANDA, new SimpleGrantedAuthority("PDV_COMANDA_COURTESY"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SESSION_ESSENCE_REQUIRED"));
    }

    @Test
    void manage_tiers_with_only_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sessao/faixas").with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/pdv/sessao/faixas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"preco\":10}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/pdv/sessao/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeVasoGrandePreco\":10}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_tier_with_sessao_manage_returns_201_and_repeated_name_409() throws Exception {
        String nome = "Faixa " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"nome\":\"" + nome + "\",\"preco\":30.00,\"marcas\":\"Luk, Smynar, Nay\",\"ordem\":2}";
        mockMvc.perform(post("/pdv/sessao/faixas").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preco").value(30.00))
                .andExpect(jsonPath("$.ativo").value(true));
        mockMvc.perform(post("/pdv/sessao/faixas").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_MENU_CONFLICT"));
    }

    @Test
    void add_session_without_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/1/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":1,\"essencia\":\"Zomo\"}")
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_session_to_unknown_comanda_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":1,\"essencia\":\"Zomo\"}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void add_session_without_essence_returns_400() throws Exception {
        mockMvc.perform(post("/pdv/comandas/1/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":1,\"essencia\":\"\"}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isBadRequest());
    }

    // ── PDV-F023 ──

    @Test
    void session_status_without_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/pdv/comandas/1/sessoes/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENTREGUE\"}")
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void session_status_on_unknown_comanda_returns_404_and_invalid_status_400() throws Exception {
        mockMvc.perform(patch("/pdv/comandas/999999/sessoes/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENTREGUE\"}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/pdv/comandas/999999/sessoes/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isBadRequest());
    }

    // ── PDV-F024 ──

    @Test
    void addons_are_managed_only_with_sessao_manage_and_listed_in_the_menu() throws Exception {
        mockMvc.perform(get("/pdv/sessao/adicionais").with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isForbidden());
        String nome = "Adicional " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"nome\":\"" + nome + "\",\"preco\":5.00,\"ordem\":1}";
        mockMvc.perform(post("/pdv/sessao/adicionais").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true));
        mockMvc.perform(post("/pdv/sessao/adicionais").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_MENU_CONFLICT"));
        mockMvc.perform(get("/pdv/sessao/cardapio").with(user("atendente").authorities(COMANDA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adicionais[?(@.nome == '" + nome + "')]").exists());
    }

    @Test
    void update_unknown_addon_returns_404() throws Exception {
        mockMvc.perform(put("/pdv/sessao/adicionais/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"preco\":1}")
                        .with(user("gerente").authorities(SESSAO_MANAGE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SESSION_ADDON_NOT_FOUND"));
    }
}
