package com.cernecommerce.adapter.in.controller;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDV-F043 — quem sincroniza é o caixa; quem reenvia ou descarta é o gerente. */
@SpringBootTest
@ActiveProfiles("dev")
class PdvOfflineControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void sync_semAutenticacao_responde401() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales/sync").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /** Corpo válido: com corpo inválido a validação responde 400 antes de o @PreAuthorize rodar. */
    private static final String LOTE = """
            {"sales":[{"clientSaleId":"3f1c9a2e-7b4d-4c1e-9a8f-2d6b5e0c1a7b","soldAt":"2026-10-09T15:00:00Z",
              "items":[{"sku":"LM-AZUL-MACO","quantity":1}],
              "payments":[{"method":"DINHEIRO","amount":12.00}]}]}""";

    @Test
    void sync_semPdvSaleManage_responde403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales/sync").contentType(MediaType.APPLICATION_JSON).content(LOTE)
                        .with(user("bob").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    /** O atendente sincroniza a própria fila, mas não decide o destino da venda recusada. */
    @Test
    void retry_doAtendente_responde403() throws Exception {
        mockMvc.perform(post("/pdv/offline-rejections/1/retry")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    /** Com PDV_OFFLINE_REVIEW o RBAC deixa passar; o 404 é da recusa inexistente na base de teste. */
    @Test
    void retry_comOfflineReview_chegaNaRota() throws Exception {
        mockMvc.perform(post("/pdv/offline-rejections/999999/retry")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_OFFLINE_REVIEW"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void discard_doAtendente_responde403() throws Exception {
        mockMvc.perform(post("/pdv/offline-rejections/1/discard").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden());
    }
}
