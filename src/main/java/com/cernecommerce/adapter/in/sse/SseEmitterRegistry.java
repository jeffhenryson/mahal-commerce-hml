package com.cernecommerce.adapter.in.sse;

import com.cernecommerce.adapter.in.dtos.response.NotificationResponseDTO;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEmitterRegistry implements NotificationSsePort {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    // Era 5: um gerente com o admin em algumas abas e no celular já passava do limite (ECM-F012).
    @Value("${notification.sse.max-connections-per-user:10}")
    private int maxConnectionsPerUser = 10;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Counter notificationsSentCounter;
    private final Counter connectionRefusedCounter;

    public SseEmitterRegistry(MeterRegistry registry) {
        Gauge.builder("sse.active_connections", emitters,
                        map -> map.values().stream().mapToInt(List::size).sum())
                .description("Total active SSE connections across all users")
                .register(registry);
        this.notificationsSentCounter = registry.counter("sse.notifications.sent.total");
        this.connectionRefusedCounter = registry.counter("sse.connections.refused.total");
    }

    /**
     * O usuário já está no limite? O controller pergunta antes de abrir o stream, para recusar com
     * 429 ainda na requisição original. Recusar depois, pelo emitter, vira um re-dispatch assíncrono
     * sem autenticação, que a segurança responde com 401, e o front lê 401 como sessão expirada.
     */
    public boolean limiteAtingido(String username) {
        List<SseEmitter> list = emitters.get(username);
        return list != null && list.size() >= maxConnectionsPerUser;
    }

    /** Rede de segurança da corrida entre {@link #limiteAtingido} e o registro. */
    public void register(String username, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>());
        if (list.size() >= maxConnectionsPerUser) {
            log.warn("sse.connection_limit_exceeded username={} limit={}", username, maxConnectionsPerUser);
            connectionRefusedCounter.increment();
            emitter.completeWithError(new IllegalStateException("SSE connection limit exceeded"));
            return;
        }
        list.add(emitter);
        emitter.onCompletion(() -> remove(username, emitter));
        emitter.onTimeout(() -> remove(username, emitter));
        emitter.onError(ex -> remove(username, emitter));
    }

    @Override
    public void send(String username, Notification notification) {
        List<SseEmitter> userEmitters = emitters.get(username);
        if (userEmitters == null || userEmitters.isEmpty()) return;

        NotificationResponseDTO payload = NotificationResponseDTO.from(notification);
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
                notificationsSentCounter.increment();
            } catch (IOException ex) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> remove(username, e));
    }

    @Override
    public void sendEvent(String username, String eventName, Map<String, Object> payload) {
        List<SseEmitter> userEmitters = emitters.get(username);
        if (userEmitters == null || userEmitters.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException ex) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> remove(username, e));
    }

    @Override
    public Set<String> connectedUsernames() {
        return Set.copyOf(emitters.keySet());
    }

    public void remove(String username, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(username);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(username, userEmitters);
            }
        }
    }

    @Override
    public int activeConnections(String username) {
        List<SseEmitter> userEmitters = emitters.get(username);
        return userEmitters == null ? 0 : userEmitters.size();
    }
}
