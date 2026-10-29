package com.cernecommerce.adapter.in.sse;

import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry(new SimpleMeterRegistry());

    private static final Notification NOTIFICATION = new Notification(
            1L, "alice", NotificationType.PASSWORD_CHANGED, "Título", "Corpo", null, Instant.now());

    @Test
    void register_adiciona_emitter_para_usuario() {
        registry.register("alice", new SseEmitter(1000L));

        assertThat(registry.activeConnections("alice")).isEqualTo(1);
    }

    @Test
    void register_aceita_multiplos_emitters_por_usuario_ate_o_limite() {
        for (int i = 0; i < 10; i++) {
            registry.register("alice", new SseEmitter(1000L));
        }

        assertThat(registry.activeConnections("alice")).isEqualTo(10);
    }

    @Test
    void limiteAtingido_so_com_o_limite_cheio() {
        assertThat(registry.limiteAtingido("alice")).isFalse();
        for (int i = 0; i < 9; i++) {
            registry.register("alice", new SseEmitter(1000L));
        }
        assertThat(registry.limiteAtingido("alice")).isFalse();

        registry.register("alice", new SseEmitter(1000L));

        assertThat(registry.limiteAtingido("alice")).isTrue();
        assertThat(registry.limiteAtingido("bob")).isFalse();
    }

    @Test
    void register_recusa_conexao_alem_do_limite() {
        for (int i = 0; i < 10; i++) {
            registry.register("alice", new SseEmitter(1000L));
        }

        SseEmitter extra = new SseEmitter(1000L);
        registry.register("alice", extra);

        assertThat(registry.activeConnections("alice")).isEqualTo(10);
    }

    @Test
    void send_para_usuario_sem_emitters_nao_lanca_excecao() {
        assertThat(registry.activeConnections("nobody")).isZero();
        registry.send("nobody", NOTIFICATION);
    }

    @Test
    void remove_diminui_contagem_de_emitters() {
        SseEmitter emitter = new SseEmitter(1000L);
        registry.register("alice", emitter);
        assertThat(registry.activeConnections("alice")).isEqualTo(1);

        registry.remove("alice", emitter);

        assertThat(registry.activeConnections("alice")).isZero();
    }

    @Test
    void remove_usuario_inexistente_nao_lanca_excecao() {
        registry.remove("nobody", new SseEmitter(1000L));
    }

    @Test
    void activeConnections_retorna_zero_para_usuario_sem_emitters() {
        assertThat(registry.activeConnections("nobody")).isZero();
    }
}
