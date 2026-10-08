package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.ReceivableFilter;
import com.cernecommerce.core.ports.in.AutomationDispatchUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase.ReceivableView;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Marca como VENCIDO o marcado em aberto cujo prazo passou (CRM-F010).
 *
 * <p>Roda logo depois da meia-noite da loja. A regra "quem tem vencido não marca" não depende dele —
 * {@code ReceivableService} calcula o vencido na leitura —, mas o status gravado é o que a tabela de
 * marcados e o resumo por cliente mostram.</p>
 *
 * <p>Depois de marcar, dispara as automações de MARCADO_VENCIDO para quem tem marcado que venceu
 * ontem (o que acabou de virar vencido) — uma ocorrência por cliente e vencimento.</p>
 */
@Component
public class ReceivableOverdueJob {

    private static final Logger log = LoggerFactory.getLogger(ReceivableOverdueJob.class);
    private static final int PAGE_SIZE = 500;

    private final ReceivableUseCase receivableUseCase;
    private final AutomationDispatchUseCase automationDispatch;
    private final Clock clock;

    @Autowired
    public ReceivableOverdueJob(ReceivableUseCase receivableUseCase, AutomationDispatchUseCase automationDispatch) {
        this(receivableUseCase, automationDispatch, Clock.system(EmailFormat.ZONE));
    }

    ReceivableOverdueJob(ReceivableUseCase receivableUseCase, AutomationDispatchUseCase automationDispatch,
            Clock clock) {
        this.receivableUseCase = receivableUseCase;
        this.automationDispatch = automationDispatch;
        this.clock = clock;
    }

    @Scheduled(cron = "${pdv.on-account.overdue.cron:0 5 0 * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "receivableOverdue", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void markOverdue() {
        log.info("scheduler.receivable.overdue.start");
        int marked = receivableUseCase.markOverdue();
        log.info("scheduler.receivable.overdue.done marked={}", marked);
        dispatchOverdueAutomations(LocalDate.now(clock).minusDays(1));
    }

    void dispatchOverdueAutomations(LocalDate dueDate) {
        Map<Long, List<CustomerReceivable>> byCustomer = new LinkedHashMap<>();
        try {
            ReceivableFilter filter = new ReceivableFilter(null, null, null, dueDate, dueDate, null, null, null, null);
            for (ReceivableView view : receivableUseCase.list(filter, 0, PAGE_SIZE).content()) {
                CustomerReceivable r = view.receivable();
                if (r.status().isOpen()) {
                    byCustomer.computeIfAbsent(r.customerId(), id -> new ArrayList<>()).add(r);
                }
            }
        } catch (Exception ex) {
            log.error("scheduler.receivable.overdue.list.failed dueDate={} error={}", dueDate, ex.getMessage());
            return;
        }
        byCustomer.forEach((customerId, receivables) -> {
            try {
                BigDecimal total = receivables.stream()
                        .map(CustomerReceivable::amountOpen)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                automationDispatch.dispatch(AutomationOccurrence.event(AutomationEvent.MARCADO_VENCIDO, customerId,
                        null, "MARCADO:" + customerId + ":" + dueDate + ":VENCIDO",
                        Map.of("vencimento", dueDate.toString(), "total", total, "quantidade", receivables.size())));
            } catch (Exception ex) {
                log.error("scheduler.receivable.overdue.automation.failed customerId={} error={}", customerId,
                        ex.getMessage());
            }
        });
    }
}
