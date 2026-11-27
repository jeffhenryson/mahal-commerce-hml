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
 * Pedidos de clientes: ESTOQUE_PRODUCT_REQUEST_CREATE (atendente) só registra, junta e consulta parecidos;
 * status, aviso, vínculo de SKU e a listagem exigem ESTOQUE_PRODUCT_REQUEST_MANAGE.
 */
@SpringBootTest
@ActiveProfiles("dev")
public class ProductRequestControllerSecurityTest {

    private static final String BASE = "/estoque/product-requests";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor atendente() {
        return user("caixa").authorities(new SimpleGrantedAuthority("ESTOQUE_PRODUCT_REQUEST_CREATE"));
    }

    @Test
    void sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void create_nao_lista() throws Exception {
        mockMvc.perform(get(BASE).with(atendente())).andExpect(status().isForbidden());
    }

    @Test
    void create_nao_muda_status() throws Exception {
        mockMvc.perform(patch(BASE + "/1/status").with(atendente())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ORDERED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_nao_avisa() throws Exception {
        mockMvc.perform(post(BASE + "/1/notify").with(atendente())
                .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"MANUAL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_nao_vincula_sku() throws Exception {
        mockMvc.perform(post(BASE + "/1/link-sku").with(atendente())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_nao_cancela_solicitacao() throws Exception {
        mockMvc.perform(delete(BASE + "/1/entries/1").with(atendente())).andExpect(status().isForbidden());
    }

    @Test
    void create_consulta_parecidos() throws Exception {
        mockMvc.perform(get(BASE + "/similar").param("description", "essencia inexistente xyz").with(atendente()))
                .andExpect(status().isOk());
    }

    @Test
    void create_registra_e_valida_cliente() throws Exception {
        mockMvc.perform(post(BASE).with(atendente())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Essência Teste\",\"entry\":{\"quantity\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_REQUIRED"));
    }
}
