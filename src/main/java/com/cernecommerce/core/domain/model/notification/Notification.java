package com.cernecommerce.core.domain.model.notification;

import java.time.Instant;
import java.util.Map;

/**
 * @param details  pares "rótulo: valor" dos alertas, para o cliente não depender do texto de {@code body}
 * @param metadata ligações com outros registros, como {@code failedEmailId}
 */
public record Notification(
    Long id,
    String username,
    NotificationType type,
    String title,
    String body,
    String targetUrl,
    Instant readAt,
    Instant createdAt,
    Map<String, String> details,
    Map<String, String> metadata
) {
    public Notification(Long id, String username, NotificationType type, String title, String body, String targetUrl,
            Instant readAt, Instant createdAt) {
        this(id, username, type, title, body, targetUrl, readAt, createdAt, null, null);
    }

    /** Compatibilidade com chamadores anteriores ao {@code targetUrl} — nasce sem vínculo. */
    public Notification(Long id, String username, NotificationType type, String title, String body,
            Instant readAt, Instant createdAt) {
        this(id, username, type, title, body, null, readAt, createdAt);
    }

    public boolean isRead() {
        return readAt != null;
    }
}
