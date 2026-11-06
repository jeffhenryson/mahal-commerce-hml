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

import com.cernecommerce.core.domain.model.notification.FailedEmail;
import com.cernecommerce.core.ports.out.notification.FailedEmailRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
    private final FailedEmailRepository failedEmails;
    private final Executor executor;
    private final Clock clock;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    @Autowired
    public DevAlertNotifier(OperationalEmailDispatcher dispatcher, NotificationUseCase notificationUseCase,
            NotificationSsePort ssePort, UserRepository userRepository, FailedEmailRepository failedEmails,
            @Qualifier("taskExecutor") Executor executor) {
        this(dispatcher, notificationUseCase, ssePort, userRepository, failedEmails, executor, Clock.systemUTC());
    }

    DevAlertNotifier(OperationalEmailDispatcher dispatcher, NotificationUseCase notificationUseCase,
            NotificationSsePort ssePort, UserRepository userRepository, FailedEmailRepository failedEmails,
            Executor executor, Clock clock) {
        this.dispatcher = dispatcher;
        this.notificationUseCase = notificationUseCase;
        this.ssePort = ssePort;
        this.userRepository = userRepository;
        this.failedEmails = failedEmails;
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
            notifyInApp(subject, body, new LinkedHashMap<>(details), null);
            dispatcher.toRole(DEV_ROLE, NotificationType.DEV, email);
        });
    }

    @Override
    public void emailDeliveryFailed(String emailType, String to, String error, String subject, String html) {
        String failedEmailId = store(emailType, to, error, subject, html);
        if (!firstInWindow("email-failed|" + emailType)) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Tipo", emailType);
        details.put("Destinatário", to);
        details.put("Erro", String.valueOf(error));
        Map<String, String> metadata = failedEmailId == null ? null : Map.of("failedEmailId", failedEmailId);
        run(() -> notifyInApp("Falha ao enviar e-mail",
                "Tipo: " + emailType + "\nDestinatário: " + to + "\nErro: " + error, details, metadata));
    }

    /** Guarda sempre, mesmo dentro da janela do aviso. Falha aqui não pode virar outra falha de envio. */
    private String store(String emailType, String to, String error, String subject, String html) {
        try {
            return String.valueOf(failedEmails.save(FailedEmail.of(emailType, to, subject, html,
                    String.valueOf(error), clock.instant())).id());
        } catch (Exception ex) {
            log.error("dev-alert.failed-email.store.failed type={} error={}", emailType, ex.getMessage());
            return null;
        }
    }

    private void notifyInApp(String title, String body, Map<String, String> details, Map<String, String> metadata) {
        for (String username : userRepository.findUsernamesByRole(DEV_ROLE)) {
            if (!dispatcher.preference(username, NotificationType.DEV).inAppEnabled()) {
                continue;
            }
            try {
                Notification saved = notificationUseCase.notifyStructured(username, NotificationType.DEV, title, body,
                        details, metadata);
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
