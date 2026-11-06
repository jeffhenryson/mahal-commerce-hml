package com.cernecommerce.core.domain.exception.notification;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(Long id) {
        super("Notificação não encontrada: id=" + id);
    }
}
