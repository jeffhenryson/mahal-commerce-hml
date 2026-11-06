package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.token.PendingLoginTokenRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PendingLoginTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(PendingLoginTokenCleanupService.class);

    private final PendingLoginTokenRepository repository;

    public PendingLoginTokenCleanupService(PendingLoginTokenRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${auth.pending-login.cleanup.cron:0 35 3 * * *}")
    @SchedulerLock(name = "pendingLoginTokenCleanup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT2M")
    public void cleanup() {
        log.info("scheduler.pending-login.cleanup.start");
        repository.deleteExpiredBefore(Instant.now());
        log.info("scheduler.pending-login.cleanup.done");
    }
}
