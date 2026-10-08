package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxo end-to-end de SystemConfig:
 *   GET /system/config/public (público) → PUT /system/config/{key} (DEV_ELEVATED)
 *   → cache eviction via @CacheEvict → MaintenanceModeFilter reage à mudança.
 *
 * Nota: dev profile usa Flyway desabilitado (create-drop via Hibernate),
 * portanto system_config começa vazio. Maintenance mode é configurado
 * diretamente via SystemConfigPort para contornar a whitelist de PUBLIC_KEYS.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SystemConfigFlowIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SystemConfigPort configPort;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void resetMaintenanceMode() {
        configPort.save(new SystemConfig("security.maintenance.enabled", "false", Instant.now(), "test-cleanup"));
    }

    // ── GET /system/config/public ────────────────────────────────────────────

    @Test
    void getPublicConfig_sem_auth_retorna_200() throws Exception {
        mvc.perform(get("/system/config/public"))
                .andExpect(status().isOk());
    }

    // ── GET /system/config ──────────────────────────────────────────────────

    @Test
    void getAll_sem_auth_retorna_401() throws Exception {
        mvc.perform(get("/system/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_com_dev_elevated_retorna_200() throws Exception {
        mvc.perform(get("/system/config")
                        .with(user("devuser").authorities(new SimpleGrantedAuthority("DEV_ELEVATED"))))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_sem_dev_elevated_retorna_403() throws Exception {
        mvc.perform(get("/system/config")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    // ── PUT /system/config/{key} ────────────────────────────────────────────

    @Test
    void set_sem_auth_retorna_401() throws Exception {
        mvc.perform(put("/system/config/auth.forgot-password.enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"true\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void set_sem_dev_elevated_retorna_403() throws Exception {
        mvc.perform(put("/system/config/auth.forgot-password.enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"true\"}")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void set_chave_invalida_retorna_400() throws Exception {
        mvc.perform(put("/system/config/chave.invalida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"x\"}")
                        .with(user("devuser").authorities(new SimpleGrantedAuthority("DEV_ELEVATED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void set_chave_publica_persiste_e_reflete_em_getPublicConfig() throws Exception {
        mvc.perform(put("/system/config/auth.forgot-password.enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"false\"}")
                        .with(user("devuser").authorities(new SimpleGrantedAuthority("DEV_ELEVATED"))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/system/config/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['auth.forgot-password.enabled']").value("false"));
    }

    // ── Maintenance mode: cache eviction → MaintenanceModeFilter ───────────

    @Test
    void maintenance_on_evicta_cache_e_filter_bloqueia_requests_com_503() throws Exception {
        // save direto no port evicta @CacheEvict(allEntries=true) do SystemConfigAdapter
        configPort.save(new SystemConfig("security.maintenance.enabled", "true", Instant.now(), "test"));

        // qualquer path não-allowlistado deve ser interceptado pelo MaintenanceModeFilter
        mvc.perform(get("/system/config"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void maintenance_on_nao_bloqueia_system_config_public() throws Exception {
        configPort.save(new SystemConfig("security.maintenance.enabled", "true", Instant.now(), "test"));

        // /system/config/public está na allowlist do MaintenanceModeFilter
        mvc.perform(get("/system/config/public"))
                .andExpect(status().isOk());
    }
}
