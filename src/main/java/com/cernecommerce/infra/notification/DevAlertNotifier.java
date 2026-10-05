package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Row;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Tone;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.ports.out.notification.DevAlertPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * {@link DevAlertPort} da aplicação: e-mail + in-app para a {@value #DEV_ROLE}, com no máximo um
 * alerta por {@code dedupKey} a cada {@link #WINDOW}. Sem o agrupamento, um bug que derruba toda
 * requisição de uma rota mandaria um e-mail por requisição.
 *
 * <p>O agrupamento é em memória, por instância: perder a janela num restart só custa um e-mail a mais.</p>
 */
@Component
public class DevAlertNotifier implements DevAlertPort {

    static final String DEV_ROLE = "ROLE_DEV";
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_KEYS = 1_000;

    private static final Logger log = LoggerFactory.getLogger(DevAlertNotifier.class);

    private final OperationalEmailDispatcher dispatcher;
    private final NotificationUseCase notificationUseCase;
    private final NotificationSsePort ssePort;
    private final UserRepository userRepository;
    private final Executor executor;
    private final Clock clock;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    @Autowired
    public DevAlertNotifier(OperationalEmailDispatcher dispatcher, NotificationUseCase notificationUseCase,
            NotificationSsePort ssePort, UserRepository userRepository, @Qualifier("taskExecutor") Executor executor) {
        this(dispatcher, notificationUseCase, ssePort, userRepository, executor, Clock.systemUTC());
    }

    DevAlertNotifier(OperationalEmailDispatcher dispatcher, NotificationUseCase notificationUseCase,
            NotificationSsePort ssePort, UserRepository userRepository, Executor executor, Clock clock) {
        this.dispatcher = dispatcher;
        this.notificationUseCase = notificationUseCase;
        this.ssePort = ssePort;
        this.userRepository = userRepository;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public void alert(String category, String dedupKey, String subject, Map<String, String> details) {
        if (!firstInWindow(category + "|" + dedupKey)) {
            return;
        }
        List<Row> rows = details.entrySet().stream().map(e -> Row.of(e.getKey(), e.getValue())).toList();
        NotificationEmail email = NotificationEmail.builder("dev." + category, subject)
                .tone(Tone.DANGER)
                .intro("Alerta técnico automático. Repetições deste alerta nos próximos "
                        + WINDOW.toMinutes() + " minutos não geram novo e-mail.")
                .section("Detalhes", rows)
                .build();
        String body = rows.stream().map(r -> r.label() + ": " + r.value()).reduce((a, b) -> a + "\n" + b).orElse("");
        run(() -> {
            notifyInApp(subject, body);
            dispatcher.toRole(DEV_ROLE, NotificationType.DEV, email);
        });
    }

    @Override
    public void emailDeliveryFailed(String emailType, String to, String error) {
        if (!firstInWindow("email-failed|" + emailType)) {
            return;
        }
        run(() -> notifyInApp("Falha ao enviar e-mail",
                "Tipo: " + emailType + "\nDestinatário: " + to + "\nErro: " + error));
    }

    private void notifyInApp(String title, String body) {
        for (String username : userRepository.findUsernamesByRole(DEV_ROLE)) {
            if (!dispatcher.preference(username, NotificationType.DEV).inAppEnabled()) {
                continue;
            }
            try {
                Notification saved = notificationUseCase.notify(username, NotificationType.DEV, title, body);
                ssePort.send(username, saved);
            } catch (Exception ex) {
                log.error("dev-alert.inapp.failed username={} error={}", username, ex.getMessage());
            }
        }
    }

    private boolean firstInWindow(String key) {
        Instant now = clock.instant();
        if (lastSent.size() > MAX_TRACKED_KEYS) {
            lastSent.values().removeIf(sentAt -> sentAt.plus(WINDOW).isBefore(now));
        }
        boolean[] first = {false};
        lastSent.compute(key, (k, sentAt) -> {
            if (sentAt == null || !sentAt.plus(WINDOW).isAfter(now)) {
                first[0] = true;
                return now;
            }
            return sentAt;
        });
        return first[0];
    }

    private void run(Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    log.error("dev-alert.dispatch.failed error={}", ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.error("dev-alert.schedule.failed error={}", ex.getMessage());
        }
    }
}
