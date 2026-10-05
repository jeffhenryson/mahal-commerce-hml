package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.ManagerNotificationPort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Entrega um {@link NotificationEmail} a um grupo — quem tem a permissão ou a role — respeitando a
 * preferência de e-mail de cada um ({@link NotificationPreference#emailEnabled()}). É o mesmo
 * critério das notificações in-app de estoque, que já escolhiam os destinatários por permissão.
 */
@Component
public class OperationalEmailDispatcher implements ManagerNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(OperationalEmailDispatcher.class);

    private final NotificationPreferenceUseCase preferenceUseCase;
    private final UserRepository userRepository;
    private final EmailPort emailPort;

    public OperationalEmailDispatcher(NotificationPreferenceUseCase preferenceUseCase, UserRepository userRepository,
            EmailPort emailPort) {
        this.preferenceUseCase = preferenceUseCase;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
    }

    public void toPermission(String permission, NotificationType type, NotificationEmail email) {
        toPermission(permission, type, email, null);
    }

    @Override
    public void emailPermission(String permission, NotificationType type, NotificationEmail email) {
        toPermission(permission, type, email);
    }

    /** Igual a {@link #toPermission(String, NotificationType, NotificationEmail)}, sem avisar {@code exceptUsername} — quem fez a ação já sabe dela. */
    public void toPermission(String permission, NotificationType type, NotificationEmail email, String exceptUsername) {
        try {
            sendAll(userRepository.findUsernamesByPermission(permission).stream()
                    .filter(username -> !username.equals(exceptUsername))
                    .toList(), type, email);
        } catch (Exception ex) {
            log.error("notification.operational.failed permission={} category={} error={}",
                    permission, email.category(), ex.getMessage());
        }
    }

    public void toRole(String role, NotificationType type, NotificationEmail email) {
        try {
            sendAll(userRepository.findUsernamesByRole(role), type, email);
        } catch (Exception ex) {
            log.error("notification.operational.failed role={} category={} error={}",
                    role, email.category(), ex.getMessage());
        }
    }

    /** Preferência do usuário para o tipo; na falta (ou falha ao ler), o padrão: tudo ligado. */
    public NotificationPreference preference(String username, NotificationType type) {
        try {
            List<NotificationPreference> prefs = preferenceUseCase.getPreferences(username);
            return prefs.stream()
                    .filter(p -> p.type() == type)
                    .findFirst()
                    .orElseGet(() -> NotificationPreference.defaultFor(username, type));
        } catch (Exception ex) {
            log.warn("notification.preference.resolve.failed username={} type={} — using defaults", username, type);
            return NotificationPreference.defaultFor(username, type);
        }
    }

    private void sendAll(Collection<String> usernames, NotificationType type, NotificationEmail email) {
        for (String username : usernames) {
            if (!preference(username, type).emailEnabled()) {
                continue;
            }
            try {
                userRepository.findByUsername(username)
                        .map(u -> u.getEmail())
                        .filter(address -> address != null && !address.isBlank())
                        .ifPresent(address -> emailPort.sendNotification(address, email));
            } catch (Exception ex) {
                log.error("notification.operational.email.failed username={} category={} error={}",
                        username, email.category(), ex.getMessage());
            }
        }
    }
}
