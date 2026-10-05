package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationType;

/** E-mail operacional para um grupo definido por permissão, conforme a preferência de e-mail de cada um. */
public interface ManagerNotificationPort {

    void emailPermission(String permission, NotificationType type, NotificationEmail email);
}
