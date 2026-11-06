package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.Notification;

import java.time.Instant;

public interface NotificationRepository {

    Notification save(Notification notification);

    PageResult<Notification> findByUsername(String username, boolean unreadOnly, int page, int size);

    /** Só do dono: de outra pessoa é o mesmo que não existir. */
    java.util.Optional<Notification> findById(Long id, String username);

    void markAsRead(Long id, String username);

    void markAllAsRead(String username);

    long countUnread(String username);

    void delete(Long id, String username);

    void deleteReadBefore(Instant before);
}
