package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    SystemConfigPort configPort;

    @InjectMocks
    SystemConfigService service;

    private static SystemConfig config(String key, String value) {
        return new SystemConfig(key, value, Instant.now(), "system");
    }

    // ── getAllPublic ──────────────────────────────────────────────────────────

    @Test
    void getAllPublic_retorna_apenas_chaves_publicas() {
        when(configPort.findAll()).thenReturn(List.of(
            config("auth.google.enabled", "true"),
            config("auth.forgot-password.enabled", "false"),
            config("auth.registration.enabled", "false"),
            config("chave.interna", "secreto")
        ));

        Map<String, String> result = service.getAllPublic();

        assertThat(result).containsOnlyKeys("auth.google.enabled", "auth.forgot-password.enabled");
    }

    @Test
    void getAllPublic_retorna_map_vazio_quando_sem_registros() {
        when(configPort.findAll()).thenReturn(List.of());
        assertThat(service.getAllPublic()).isEmpty();
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_retorna_todas_as_chaves() {
        when(configPort.findAll()).thenReturn(List.of(
            config("auth.google.enabled", "true"),
            config("auth.registration.enabled", "false"),
            config("chave.interna", "valor")
        ));

        Map<String, String> result = service.getAll();

        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("chave.interna", "valor");
    }

    @Test
    void getAll_esconde_tokens_de_integracao() {
        when(configPort.findAll()).thenReturn(List.of(
            config("auth.google.enabled", "true"),
            config("integration.email.resend.api-key", "cifrado")
        ));

        assertThat(service.getAll()).containsOnlyKeys("auth.google.enabled");
    }

    // ── set ──────────────────────────────────────────────────────────────────

    @Test
    void set_salva_chave_publica_com_sucesso() {
        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);

        service.set("auth.google.enabled", "false", "admin");

        verify(configPort).save(captor.capture());
        SystemConfig saved = captor.getValue();
        assertThat(saved.key()).isEqualTo("auth.google.enabled");
        assertThat(saved.value()).isEqualTo("false");
        assertThat(saved.updatedBy()).isEqualTo("admin");
        assertThat(saved.updatedAt()).isNotNull();
    }

    @Test
    void set_rejeita_chave_nao_publica() {
        assertThatThrownBy(() -> service.set("chave.interna", "x", "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inválida");

        verify(configPort, never()).save(any());
    }

    @Test
    void set_aceita_todas_as_chaves_publicas_conhecidas() {
        for (String key : List.of(
            "auth.google.enabled",
            "auth.forgot-password.enabled"
        )) {
            assertThatCode(() -> service.set(key, "true", "admin")).doesNotThrowAnyException();
        }
    }

    @Test
    void set_rejeita_chaves_de_auto_cadastro_removidas() {
        for (String key : List.of("auth.registration.enabled", "auth.google.register.enabled")) {
            assertThatThrownBy(() -> service.set(key, "true", "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── getBoolean ───────────────────────────────────────────────────────────

    @Test
    void getBoolean_delega_para_port() {
        when(configPort.getBoolean("auth.forgot-password.enabled", true)).thenReturn(false);

        assertThat(service.getBoolean("auth.forgot-password.enabled", true)).isFalse();
    }

    @Test
    void getBoolean_retorna_default_quando_chave_ausente() {
        when(configPort.getBoolean("auth.google.enabled", true)).thenReturn(true);

        assertThat(service.getBoolean("auth.google.enabled", true)).isTrue();
    }
}
