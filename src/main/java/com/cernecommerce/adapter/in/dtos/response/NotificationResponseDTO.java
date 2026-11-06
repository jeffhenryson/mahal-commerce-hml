package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.notification.Notification;
import java.time.Instant;
import java.util.Map;

public record NotificationResponseDTO(
        Long id,
        String type,
        String title,
        String body,
        boolean read,
        Instant readAt,
        Instant createdAt,
        String targetUrl,
        Map<String, String> details,
        Map<String, String> metadata
) {
    public static NotificationResponseDTO from(Notification n) {
        return new NotificationResponseDTO(
                n.id(), n.type().name(), n.title(), n.body(),
                n.isRead(), n.readAt(), n.createdAt(), n.targetUrl(), n.details(), n.metadata());
    }
}
