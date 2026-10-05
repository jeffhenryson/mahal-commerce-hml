package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Row;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Tone;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.recebivel.ReceivableCustomerSummary;
import com.cernecommerce.core.ports.in.OrderReportUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.infra.notification.OperationalEmailDispatcher;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resumo do dia anterior para quem tem {@code FINANCEIRO_READ} (preferência {@code RESUMO}): vendas,
 * mais vendidos, caixas e fiado vencido. Sai mesmo em dia sem venda — o e-mail chegando é também a
 * prova de que o job rodou. Cada seção falha sozinha: um relatório quebrado não derruba os outros.
 */
@Component
public class DailySalesDigestJob {

    private static final int TOP_PRODUCTS = 5;
    private static final int MAX_OVERDUE_ROWS = 15;
    private static final Map<SalesChannel, String> CHANNEL_LABELS = Map.of(
            SalesChannel.BALCAO, "Balcão",
            SalesChannel.MESA, "Mesa",
            SalesChannel.MARKETPLACE, "Loja online");

    private static final Logger log = LoggerFactory.getLogger(DailySalesDigestJob.class);

    private final OrderReportUseCase orderReportUseCase;
    private final PdvUseCase pdvUseCase;
    private final ReceivableUseCase receivableUseCase;
    private final OperationalEmailDispatcher dispatcher;
    private final Clock clock;

    @Autowired
    public DailySalesDigestJob(OrderReportUseCase orderReportUseCase, PdvUseCase pdvUseCase,
            ReceivableUseCase receivableUseCase, OperationalEmailDispatcher dispatcher) {
        this(orderReportUseCase, pdvUseCase, receivableUseCase, dispatcher, Clock.system(EmailFormat.ZONE));
    }

    DailySalesDigestJob(OrderReportUseCase orderReportUseCase, PdvUseCase pdvUseCase,
            ReceivableUseCase receivableUseCase, OperationalEmailDispatcher dispatcher, Clock clock) {
        this.orderReportUseCase = orderReportUseCase;
        this.pdvUseCase = pdvUseCase;
        this.receivableUseCase = receivableUseCase;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${email.digest.cron:0 0 8 * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "dailySalesDigest", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void send() {
        dispatcher.toPermission("FINANCEIRO_READ", NotificationType.RESUMO, build());
        log.info("scheduler.daily-digest.done");
    }

    NotificationEmail build() {
        LocalDate day = LocalDate.now(clock).minusDays(1);
        Instant from = day.atStartOfDay(EmailFormat.ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(EmailFormat.ZONE).toInstant().minusMillis(1);

        NotificationEmail.Builder email = NotificationEmail.builder("gestao.resumo-diario",
                        "Resumo de " + EmailFormat.date(day))
                .tone(Tone.INFO)
                .action("Abrir painel", "/app");

        OrderSummary summary = null;
        try {
            summary = orderReportUseCase.getSummary(null, null, null, from, to);
        } catch (Exception ex) {
            log.error("scheduler.daily-digest.summary.failed error={}", ex.getMessage());
        }
        if (summary == null) {
            email.intro("Não foi possível calcular as vendas de ontem — veja o relatório no painel.");
        } else if (summary.totalOrders() == 0) {
            email.intro("Nenhuma venda ontem.");
        } else {
            email.intro(summary.totalOrders() + " pedidos, " + EmailFormat.money(summary.totalRevenueNet())
                    + " vendidos.");
            List<Row> sales = new ArrayList<>();
            sales.add(Row.highlighted("Total vendido (líquido)", EmailFormat.money(summary.totalRevenueNet())));
            sales.add(Row.of("Pedidos", summary.totalOrders()));
            sales.add(Row.of("Ticket médio", EmailFormat.money(summary.averageTicket())));
            if (summary.revenueByChannel() != null) {
                for (SalesChannel channel : SalesChannel.values()) {
                    BigDecimal revenue = summary.revenueByChannel().get(channel);
                    if (revenue != null && revenue.signum() != 0) {
                        sales.add(Row.of(CHANNEL_LABELS.getOrDefault(channel, channel.name()), EmailFormat.money(revenue)));
                    }
                }
            }
            email.section("Vendas", sales);
            if (summary.topProducts() != null) {
                email.section("Mais vendidos", summary.topProducts().stream().limit(TOP_PRODUCTS)
                        .map(p -> Row.of(p.productName() + " (" + p.quantitySold().stripTrailingZeros().toPlainString() + ")",
                                EmailFormat.money(p.revenue())))
                        .toList());
            }
        }

        try {
            List<CashRegisterSession> sessions = pdvUseCase.listSessions(
                    new CashRegisterSessionFilter(null, from, to, null), 0, 50).content();
            email.section("Caixas abertos no dia", sessions.isEmpty()
                    ? List.of(Row.of("Nenhum caixa aberto", "—"))
                    : sessions.stream().map(DailySalesDigestJob::sessionRow).toList());
        } catch (Exception ex) {
            log.error("scheduler.daily-digest.sessions.failed error={}", ex.getMessage());
        }

        try {
            List<ReceivableCustomerSummary> overdue = receivableUseCase.summary(null, true);
            if (!overdue.isEmpty()) {
                BigDecimal total = overdue.stream().map(ReceivableCustomerSummary::overdueBalance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                List<Row> rows = new ArrayList<>(overdue.stream().limit(MAX_OVERDUE_ROWS)
                        .map(c -> Row.of(c.customerName(), EmailFormat.money(c.overdueBalance())))
                        .toList());
                if (overdue.size() > MAX_OVERDUE_ROWS) {
                    rows.add(Row.of("+ " + (overdue.size() - MAX_OVERDUE_ROWS) + " clientes", "ver no painel"));
                }
                rows.add(Row.highlighted("Total vencido", EmailFormat.money(total)));
                email.section("Fiado vencido", rows);
            }
        } catch (Exception ex) {
            log.error("scheduler.daily-digest.overdue.failed error={}", ex.getMessage());
        }
        return email.build();
    }

    private static Row sessionRow(CashRegisterSession s) {
        String label = "#" + s.id() + " · " + s.operator();
        if (s.status() == CashRegisterSession.Status.OPEN) {
            return Row.highlighted(label, "ainda aberto");
        }
        BigDecimal diff = s.differenceAmount();
        if (diff != null && diff.signum() != 0) {
            return Row.highlighted(label, "diferença " + EmailFormat.money(diff));
        }
        return Row.of(label, "fechado sem diferença");
    }
}
