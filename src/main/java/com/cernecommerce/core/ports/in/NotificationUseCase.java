package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;

public interface NotificationUseCase {

    /** Sem vínculo de navegação — equivalente a {@code notify(..., targetUrl = null)}. */
    default Notification notify(String username, NotificationType type, String title, String body) {
        return notify(username, type, title, body, null);
    }

    /**
     * @param targetUrl opcional — rota do admin para onde o clique na notificação deve levar
     *        (ex.: {@code /app/estoque/kits/KIT-INICIANTE}). {@code null} quando não há destino
     *        natural.
     */
    Notification notify(String username, NotificationType type, String title, String body, String targetUrl);

    /** Com os pares do alerta e as ligações com outros registros. */
    Notification notifyStructured(String username, NotificationType type, String title, String body,
            java.util.Map<String, String> details, java.util.Map<String, String> metadata);

    /** @throws com.cernecommerce.core.domain.exception.notification.NotificationNotFoundException de outra pessoa */
    Notification getNotification(String username, Long notificationId);

    PageResult<Notification> getNotifications(String username, boolean unreadOnly, int page, int size);

    void markAsRead(String username, Long notificationId);

    void markAllAsRead(String username);

    long countUnread(String username);

    void delete(String username, Long notificationId);
}
