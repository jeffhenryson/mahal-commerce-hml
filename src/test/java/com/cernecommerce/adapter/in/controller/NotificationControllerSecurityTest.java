package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.sse.SseEmitterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
class NotificationControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SseEmitterRegistry sseRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void listar_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unread_count_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mark_as_read_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(patch("/notifications/1/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mark_all_read_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(patch("/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(delete("/notifications/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stream_sem_autenticacao_retorna_401() throws Exception {
        mockMvc.perform(get("/notifications/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stream_com_o_limite_de_conexoes_cheio_retorna_429_e_nao_401() throws Exception {
        // 401 faria o front renovar a sessão e, na terceira vez, desistir do tempo real.
        for (int i = 0; i < 10; i++) {
            sseRegistry.register("lotado", new SseEmitter(60_000L));
        }

        mockMvc.perform(get("/notifications/stream").with(user("lotado")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.errorCode").value("SSE_CONNECTION_LIMIT"));
    }
}
