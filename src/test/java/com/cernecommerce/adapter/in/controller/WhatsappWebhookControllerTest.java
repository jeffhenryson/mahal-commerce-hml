package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WhatsappWebhookControllerTest {

    private static final String APP_SECRET = "app-secret-de-teste";
    private static final String BODY = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":"
            + "[{\"id\":\"wamid.1\",\"status\":\"delivered\"}]}}]}]}";

    private WhatsappIntegrationUseCase whatsapp;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        whatsapp = mock(WhatsappIntegrationUseCase.class);
        mockMvc = mvc(APP_SECRET);
    }

    private MockMvc mvc(String appSecret) {
        return MockMvcBuilders.standaloneSetup(new WhatsappWebhookController(whatsapp, appSecret))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void verificacao_comTokenCerto_devolveOChallenge() throws Exception {
        when(whatsapp.matchesVerifyToken("meu-token")).thenReturn(true);

        mockMvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "meu-token")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().string("1158201444"));
    }

    @Test
    void verificacao_comTokenErrado_403() throws Exception {
        when(whatsapp.matchesVerifyToken("errado")).thenReturn(false);

        mockMvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "errado")
                        .param("hub.challenge", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void notificacao_assinada_200() throws Exception {
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(BODY, APP_SECRET))
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void notificacao_comAssinaturaErrada_ouAusente_401() throws Exception {
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(BODY, "outro-segredo"))
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=nao-e-hex")
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void semAppSecretConfigurado_recusaTodoPost() throws Exception {
        mvc("").perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(BODY, "qualquer"))
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
