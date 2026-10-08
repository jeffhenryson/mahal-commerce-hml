package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

public class SystemConfigControllerTest {

    private MockMvc mockMvc;
    private SystemConfigUseCase useCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("dev", null, List.of());

    @BeforeEach
    void setup() {
        useCase = mock(SystemConfigUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SystemConfigController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── GET /system/config/public ─────────────────────────────────────────

    @Test
    void getPublicConfig_retorna_200_com_flags() throws Exception {
        when(useCase.getAllPublic()).thenReturn(Map.of(
            "auth.google.enabled", "true",
            "auth.forgot-password.enabled", "false"
        ));

        mockMvc.perform(get("/system/config/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['auth.google.enabled']").value("true"))
                .andExpect(jsonPath("$['auth.forgot-password.enabled']").value("false"));
    }

    @Test
    void getPublicConfig_retorna_mapa_vazio_quando_sem_dados() throws Exception {
        when(useCase.getAllPublic()).thenReturn(Map.of());

        mockMvc.perform(get("/system/config/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /system/config ────────────────────────────────────────────────

    @Test
    void getAll_retorna_200_com_todas_as_flags() throws Exception {
        when(useCase.getAll()).thenReturn(Map.of(
            "auth.google.enabled", "true",
            "auth.forgot-password.enabled", "true"
        ));

        mockMvc.perform(get("/system/config").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['auth.google.enabled']").value("true"));
    }

    // ── PUT /system/config/{key} ─────────────────────────────────────────

    @Test
    void set_chave_valida_retorna_204() throws Exception {
        doNothing().when(useCase).set(anyString(), anyString(), anyString());

        mockMvc.perform(put("/system/config/auth.google.enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"false\"}")
                        .principal(AUTH))
                .andExpect(status().isNoContent());

        verify(useCase).set("auth.google.enabled", "false", "dev");
    }

    @Test
    void set_chave_invalida_retorna_400() throws Exception {
        doThrow(new IllegalArgumentException("Chave de configuração inválida: chave.invalida"))
                .when(useCase).set(eq("chave.invalida"), anyString(), anyString());

        mockMvc.perform(put("/system/config/chave.invalida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"x\"}")
                        .principal(AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void set_body_sem_value_retorna_400() throws Exception {
        mockMvc.perform(put("/system/config/auth.google.enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .principal(AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void set_body_vazio_retorna_400() throws Exception {
        mockMvc.perform(put("/system/config/auth.google.enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("")
                        .principal(AUTH))
                .andExpect(status().isBadRequest());
    }
}
