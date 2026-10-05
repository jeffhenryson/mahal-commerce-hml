package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Row;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Tone;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import com.cernecommerce.infra.notification.OperationalEmailDispatcher;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Avisa quando um caixa passa de {@link CashRegisterSession#SUGGEST_CLOSE_AFTER} aberto — o mesmo
 * limite em que a tela sugere fechar. Roda de hora em hora e pega só os caixas que cruzaram o limite
 * na última hora: cada sessão gera um aviso, sem precisar guardar quem já foi avisado.
 *
 * <p>Vai para quem tem {@code FINANCEIRO_READ} (conforme a preferência de CAIXA) e para o operador
 * do caixa, que é quem pode fechá-lo.</p>
 */
@Component
public class CashSessionStaleAlertJob {

    static final Duration RUN_EVERY = Duration.ofHours(1);
    private static final int PAGE_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(CashSessionStaleAlertJob.class);

    private final PdvUseCase pdvUseCase;
    private final OperationalEmailDispatcher dispatcher;
    private final UserRepository userRepository;
    private final EmailPort emailPort;
    private final Clock clock;

    @Autowired
    public CashSessionStaleAlertJob(PdvUseCase pdvUseCase, OperationalEmailDispatcher dispatcher,
            UserRepository userRepository, EmailPort emailPort) {
        this(pdvUseCase, dispatcher, userRepository, emailPort, Clock.systemUTC());
    }

    CashSessionStaleAlertJob(PdvUseCase pdvUseCase, OperationalEmailDispatcher dispatcher,
            UserRepository userRepository, EmailPort emailPort, Clock clock) {
        this.pdvUseCase = pdvUseCase;
        this.dispatcher = dispatcher;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.clock = clock;
    }

    @Scheduled(cron = "${pdv.session.stale-alert.cron:0 0 * * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "cashSessionStaleAlert", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void alert() {
        Instant now = clock.instant();
        Instant crossedTo = now.minus(CashRegisterSession.SUGGEST_CLOSE_AFTER);
        Instant crossedFrom = crossedTo.minus(RUN_EVERY);
        List<CashRegisterSession> stale;
        try {
            stale = pdvUseCase.listSessions(
                    new CashRegisterSessionFilter(CashRegisterSession.Status.OPEN, crossedFrom, crossedTo, null), 0, PAGE_SIZE)
                    .content().stream()
                    // O filtro é inclusivo nas duas pontas; aqui fica (from, to] para não avisar duas vezes.
                    .filter(s -> s.openedAt().isAfter(crossedFrom) && !s.openedAt().isAfter(crossedTo))
                    .toList();
        } catch (Exception ex) {
            log.error("scheduler.cash-session.stale.list.failed error={}", ex.getMessage());
            return;
        }
        for (CashRegisterSession session : stale) {
            NotificationEmail email = email(session, now);
            dispatcher.toPermission("FINANCEIRO_READ", NotificationType.CAIXA, email, session.operator());
            notifyOperator(session.operator(), email);
        }
        log.info("scheduler.cash-session.stale.done alerted={}", stale.size());
    }

    private void notifyOperator(String operator, NotificationEmail email) {
        if (!dispatcher.preference(operator, NotificationType.CAIXA).emailEnabled()) {
            return;
        }
        try {
            userRepository.findByUsername(operator)
                    .map(u -> u.getEmail())
                    .filter(address -> address != null && !address.isBlank())
                    .ifPresent(address -> emailPort.sendNotification(address, email));
        } catch (Exception ex) {
            log.error("scheduler.cash-session.stale.operator.failed operator={} error={}", operator, ex.getMessage());
        }
    }

    static NotificationEmail email(CashRegisterSession session, Instant now) {
        long hours = Duration.between(session.openedAt(), now).toHours();
        return NotificationEmail.builder("caixa.esquecido",
                        "Caixa #" + session.id() + " aberto há " + hours + "h por " + session.operator())
                .tone(Tone.WARNING)
                .intro("Caixa aberto há mais de " + CashRegisterSession.SUGGEST_CLOSE_AFTER.toHours()
                        + " horas. Se o turno acabou, feche e confira a gaveta.")
                .section(null, List.of(
                        Row.of("Operador", session.operator()),
                        Row.of("Aberto em", EmailFormat.dateTime(session.openedAt())),
                        Row.of("Depósito", session.warehouseCode())))
                .action("Abrir PDV", "/app/pdv")
                .build();
    }
}
