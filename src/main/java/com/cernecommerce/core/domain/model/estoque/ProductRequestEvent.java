package com.cernecommerce.core.domain.model.estoque;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Linha do histórico do pedido de cliente. {@code actor} é o username (ou {@code "sistema"} no gatilho de
 * entrada de estoque); o nome de exibição é resolvido na leitura.
 */
public record ProductRequestEvent(
        Long id,
        Long requestId,
        Long entryId,
        ProductRequestEventType type,
        ProductRequestStatus fromStatus,
        ProductRequestStatus toStatus,
        Map<String, Object> payload,
        String actor,
        Instant at) {

    public static final String SYSTEM_ACTOR = "sistema";

    public ProductRequestEvent {
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor é obrigatório");
        }
        // LinkedHashMap aceita valor nulo (ex.: { sku: null } ao desvincular), o que Map.copyOf não aceita.
        payload = payload == null || payload.isEmpty() ? null : java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(payload));
    }

    public static ProductRequestEvent of(Long requestId, Long entryId, ProductRequestEventType type,
            Map<String, Object> payload, String actor, Instant at) {
        return new ProductRequestEvent(null, requestId, entryId, type, null, null, payload, actor, at);
    }

    public static ProductRequestEvent statusChanged(Long requestId, ProductRequestStatus from, ProductRequestStatus to,
            Map<String, Object> payload, String actor, Instant at) {
        return new ProductRequestEvent(null, requestId, null, ProductRequestEventType.STATUS_CHANGED, from, to,
                payload, actor, at);
    }
}
