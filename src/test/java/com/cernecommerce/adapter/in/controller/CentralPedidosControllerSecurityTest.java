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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ECM-F013 — Central de Pedidos: configuração, indicadores, status público e visibilidade em massa. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CentralPedidosControllerSecurityTest {

    private static final String CONFIG = """
            {"appRecebendo":true,"qrRecebendo":false,
             "horarios":[{"diaSemana":5,"abre":"18:00","fecha":"02:00"}],
             "aceiteAutomatico":false,"tempoPreparoMin":20,"tempoEntregaMin":40,"expiracaoSolicitacaoMin":15,
             "entrega":{"habilitada":true,"taxaFixa":8.00,"pedidoMinimo":30.00,"raioKm":null,
                        "bairros":[{"nome":"Centro","taxa":5.00}]},
             "retiradaHabilitada":true,"pixChave":"pix@mahal.com"}
            """;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor com(String... permissoes) {
        return user("gerente").authorities(java.util.Arrays.stream(permissoes)
                .map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void config_semAutenticacao_401_eComLeitura_200ComOPadrao() throws Exception {
        mockMvc.perform(get("/central-pedidos/config")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/central-pedidos/config").with(com("ONLINE_ORDER_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appRecebendo").value(true))
                .andExpect(jsonPath("$.expiracaoSolicitacaoMin").value(30));
    }

    @Test
    void salvarConfig_soComOnlineConfigManage_eDevolveOQueGravou() throws Exception {
        mockMvc.perform(put("/central-pedidos/config").with(com("ONLINE_ORDER_READ", "ONLINE_ORDER_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIG))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/central-pedidos/config").with(com("ONLINE_CONFIG_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[0].fecha").value("02:00"))
                .andExpect(jsonPath("$.entrega.bairros[0].taxa").value(5.00))
                .andExpect(jsonPath("$.pixChave").value("pix@mahal.com"));

        mockMvc.perform(get("/central-pedidos/config").with(com("ONLINE_ORDER_READ")))
                .andExpect(jsonPath("$.qrRecebendo").value(false))
                .andExpect(jsonPath("$.expiracaoSolicitacaoMin").value(15));

        // O cache do system_config não volta com o rollback: deixa o padrão para os outros testes do contexto.
        mockMvc.perform(put("/central-pedidos/config").with(com("ONLINE_CONFIG_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void salvarConfig_invalida_400ComOCodigo() throws Exception {
        mockMvc.perform(put("/central-pedidos/config").with(com("ONLINE_CONFIG_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIG.replace("\"expiracaoSolicitacaoMin\":15", "\"expiracaoSolicitacaoMin\":2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CENTRAL_CONFIG"));
    }

    @Test
    void indicadores_exigemLeituraERodamAsConsultas() throws Exception {
        mockMvc.perform(get("/central-pedidos/indicators").with(com("PDV_READ"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/central-pedidos/indicators?date=2026-10-23").with(com("ONLINE_ORDER_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porCanal[0].channel").value("MARKETPLACE"))
                .andExpect(jsonPath("$.porCanal[1].channel").value("MESA"))
                .andExpect(jsonPath("$.taxaRecusa.MARKETPLACE").exists());
    }

    @Test
    void storeStatus_publico() throws Exception {
        mockMvc.perform(get("/shop/store-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aberto").isBoolean());
    }

    @Test
    void visibilidadeEmMassa_exigeConfigOuProduto() throws Exception {
        String corpo = "{\"skus\":[\"NAO-EXISTE-1\"],\"visibleInMarketplace\":false}";
        mockMvc.perform(patch("/estoque/products/visibility").with(com("ONLINE_ORDER_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/estoque/products/visibility").with(com("ONLINE_CONFIG_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }
}
