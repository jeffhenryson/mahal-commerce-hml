package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Row;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Tone;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.ReceivableFilter;
import com.cernecommerce.core.ports.in.AutomationDispatchUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase.ReceivableView;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
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
 * Lembra o cliente do marcado (fiado, CRM-F010) que vence em {@value #DAYS_BEFORE} dias e do que
 * vence hoje — um e-mail por cliente por dia, com todos os marcados daquela data. Aviso de cobrança,
 * não marketing: não passa por preferência, e só sai para cliente com e-mail.
 */
@Component
public class ReceivableReminderJob {

    static final int DAYS_BEFORE = 3;
    private static final int PAGE_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(ReceivableReminderJob.class);

    private final ReceivableUseCase receivableUseCase;
    private final CustomerRepository customerRepository;
    private final EmailPort emailPort;
    private final AutomationDispatchUseCase automationDispatch;
    private final Clock clock;

    @Autowired
    public ReceivableReminderJob(ReceivableUseCase receivableUseCase, CustomerRepository customerRepository,
            EmailPort emailPort, AutomationDispatchUseCase automationDispatch) {
        this(receivableUseCase, customerRepository, emailPort, automationDispatch, Clock.system(EmailFormat.ZONE));
    }

    ReceivableReminderJob(ReceivableUseCase receivableUseCase, CustomerRepository customerRepository,
            EmailPort emailPort, AutomationDispatchUseCase automationDispatch, Clock clock) {
        this.receivableUseCase = receivableUseCase;
        this.customerRepository = customerRepository;
        this.emailPort = emailPort;
        this.automationDispatch = automationDispatch;
        this.clock = clock;
    }

    @Scheduled(cron = "${pdv.on-account.reminder.cron:0 0 9 * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "receivableReminder", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void remind() {
        LocalDate today = LocalDate.now(clock);
        int sent = remindDueOn(today.plusDays(DAYS_BEFORE), false) + remindDueOn(today, true);
        log.info("scheduler.receivable.reminder.done sent={}", sent);
    }

    private int remindDueOn(LocalDate dueDate, boolean dueToday) {
        Map<Long, List<CustomerReceivable>> byCustomer = new LinkedHashMap<>();
        try {
            ReceivableFilter filter = new ReceivableFilter(null, null, null, dueDate, dueDate, null, null, null, null);
            for (ReceivableView view : receivableUseCase.list(filter, 0, PAGE_SIZE).content()) {
                CustomerReceivable r = view.receivable();
                // VENCIDO também é aberto, mas não vence "hoje" nem "em 3 dias" — o filtro de data já o exclui.
                if (r.status().isOpen()) {
                    byCustomer.computeIfAbsent(r.customerId(), id -> new ArrayList<>()).add(r);
                }
            }
        } catch (Exception ex) {
            log.error("scheduler.receivable.reminder.list.failed dueDate={} error={}", dueDate, ex.getMessage());
            return 0;
        }
        int sent = 0;
        for (Map.Entry<Long, List<CustomerReceivable>> entry : byCustomer.entrySet()) {
            dispatchAutomations(entry.getKey(), entry.getValue(), dueDate, dueToday);
            try {
                Customer customer = customerRepository.findById(entry.getKey()).orElse(null);
                if (customer == null || customer.email() == null || customer.email().isBlank()) {
                    continue;
                }
                emailPort.sendCustomerNotice(customer.email(), email(customer, entry.getValue(), dueDate, dueToday));
                sent++;
            } catch (Exception ex) {
                log.error("scheduler.receivable.reminder.failed customerId={} error={}", entry.getKey(), ex.getMessage());
            }
        }
        return sent;
    }

    /**
     * Automações de MARCADO_VENCENDO — independem do e-mail (o destino pode ser WhatsApp). Uma
     * ocorrência por cliente, vencimento e janela (D-3 ou D0).
     */
    private void dispatchAutomations(Long customerId, List<CustomerReceivable> receivables, LocalDate dueDate,
            boolean dueToday) {
        try {
            BigDecimal total = receivables.stream()
                    .map(CustomerReceivable::amountOpen)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            automationDispatch.dispatch(AutomationOccurrence.event(AutomationEvent.MARCADO_VENCENDO, customerId, null,
                    "MARCADO:" + customerId + ":" + dueDate + ":" + (dueToday ? "D0" : "D" + DAYS_BEFORE),
                    Map.of("vencimento", dueDate.toString(),
                            "venceHoje", dueToday,
                            "total", total,
                            "quantidade", receivables.size())));
        } catch (Exception ex) {
            log.error("scheduler.receivable.reminder.automation.failed customerId={} error={}", customerId,
                    ex.getMessage());
        }
    }

    static NotificationEmail email(Customer customer, List<CustomerReceivable> receivables, LocalDate dueDate,
            boolean dueToday) {
        BigDecimal total = receivables.stream()
                .map(CustomerReceivable::amountOpen)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Row> rows = new ArrayList<>(receivables.stream()
                .map(r -> Row.of("Compra de " + EmailFormat.dateTime(r.createdAt()),
                        EmailFormat.money(r.amountOpen())))
                .toList());
        rows.add(Row.highlighted("Total", EmailFormat.money(total)));
        String when = dueToday ? "vence hoje" : "vence em " + EmailFormat.date(dueDate);
        return NotificationEmail.builder(dueToday ? "fiado.vence-hoje" : "fiado.lembrete",
                        "Seu marcado de " + EmailFormat.money(total) + " " + when)
                .title(dueToday ? "Seu marcado vence hoje" : "Lembrete: seu marcado vence em " + DAYS_BEFORE + " dias")
                .intro("Olá, " + customer.nome() + "! Passando para lembrar do pagamento do que ficou marcado na loja.")
                .tone(dueToday ? Tone.WARNING : Tone.INFO)
                .section("Vencimento " + EmailFormat.date(dueDate), rows)
                .build();
    }
}
