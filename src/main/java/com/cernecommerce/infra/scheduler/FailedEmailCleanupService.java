package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.notification.FailedEmailRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** O HTML guardado pode ter dado de cliente: não fica além do que a equipe precisa para reenviar. */
@Component
public class FailedEmailCleanupService {

    private static final Logger log = LoggerFactory.getLogger(FailedEmailCleanupService.class);

    private final FailedEmailRepository repository;
    private final long retentionDays;

    public FailedEmailCleanupService(FailedEmailRepository repository,
            @Value("${email.failed.retention-days:30}") long retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${email.failed.cleanup.cron:0 10 4 * * *}")
    @SchedulerLock(name = "failedEmailCleanup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT2M")
    public void cleanup() {
        log.info("scheduler.failed-email.cleanup.start retentionDays={}", retentionDays);
        repository.deleteCreatedBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
        log.info("scheduler.failed-email.cleanup.done");
    }
}
