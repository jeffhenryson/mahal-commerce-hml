package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.ReceivableUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marca como VENCIDO o marcado em aberto cujo prazo passou (CRM-F010).
 *
 * <p>Roda logo depois da meia-noite da loja. A regra "quem tem vencido não marca" não depende dele —
 * {@code ReceivableService} calcula o vencido na leitura —, mas o status gravado é o que a tabela de
 * marcados e o resumo por cliente mostram.</p>
 */
@Component
public class ReceivableOverdueJob {

    private static final Logger log = LoggerFactory.getLogger(ReceivableOverdueJob.class);

    private final ReceivableUseCase receivableUseCase;

    public ReceivableOverdueJob(ReceivableUseCase receivableUseCase) {
        this.receivableUseCase = receivableUseCase;
    }

    @Scheduled(cron = "${pdv.on-account.overdue.cron:0 5 0 * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "receivableOverdue", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void markOverdue() {
        log.info("scheduler.receivable.overdue.start");
        int marked = receivableUseCase.markOverdue();
        log.info("scheduler.receivable.overdue.done marked={}", marked);
    }
}
