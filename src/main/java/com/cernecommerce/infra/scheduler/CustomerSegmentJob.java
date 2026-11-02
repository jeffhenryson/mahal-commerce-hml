package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.CrmUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V183 — recalcula o segmento (Novo, Recorrente, VIP, Em Risco, Inativo) da base uma vez por dia. O
 * segmento depende do tempo desde a última compra, então muda mesmo sem nenhum evento: quem não volta
 * passa de Recorrente a Em Risco sozinho. Cliente com segmento manual fica de fora.
 */
@Component
public class CustomerSegmentJob {

    private static final Logger log = LoggerFactory.getLogger(CustomerSegmentJob.class);

    private final CrmUseCase crmUseCase;

    public CustomerSegmentJob(CrmUseCase crmUseCase) {
        this.crmUseCase = crmUseCase;
    }

    @Scheduled(cron = "${crm.segment.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "customerSegmentRecalc", lockAtMostFor = "PT15M", lockAtLeastFor = "PT30S")
    public void recalculate() {
        try {
            int updated = crmUseCase.recalculateSegments();
            log.info("scheduler.crm.segment.done updated={}", updated);
        } catch (RuntimeException e) {
            log.error("scheduler.crm.segment.failed", e);
        }
    }
}
