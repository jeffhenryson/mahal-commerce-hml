package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.notification.Notification;

import java.util.Map;
import java.util.Set;

public interface NotificationSsePort {
    void send(String username, Notification notification);
    int activeConnections(String username);

    /**
     * Evento efêmero no mesmo stream das notificações, com nome próprio e sem gravar nada — a tela
     * que o assina relê o que precisar (ECM-F012, Central de Pedidos).
     */
    void sendEvent(String username, String eventName, Map<String, Object> payload);

    /** Quem tem stream aberto agora, para não procurar destinatário entre quem nem está ouvindo. */
    Set<String> connectedUsernames();
}
