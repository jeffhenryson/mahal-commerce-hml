package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Row;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Tone;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.out.notification.DevAlertPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * E-mails para quem gere a loja e para os devs, a partir dos {@link AuditEvent}s:
 * <ul>
 *   <li><b>Caixa</b> (abertura, sangria/suprimento, fechamento com resumo) e <b>operação</b>
 *       (cancelamento, reembolso, correção de pagamento, preço) → quem tem {@value #MANAGER_PERMISSION},
 *       conforme a preferência de e-mail de cada um;</li>
 *   <li><b>Dev</b> (bug report, integração alterada, eventos de segurança e de RBAC) → {@link DevAlertPort}.</li>
 * </ul>
 * Separado do {@link NotificationEventListener}, que cuida do que vai para o próprio usuário e
 * para o comprador.
 */
@Component
public class OperationalNotificationListener {

    static final String MANAGER_PERMISSION = "FINANCEIRO_READ";
    private static final String CAIXA_PATH = "/app/pdv";
    private static final int MOVEMENTS_IN_SUMMARY = 200;

    private static final Logger log = LoggerFactory.getLogger(OperationalNotificationListener.class);

    private static final Map<PaymentMethod, String> PAYMENT_LABELS = Map.of(
            PaymentMethod.DINHEIRO, "Dinheiro",
            PaymentMethod.DEBITO, "Débito",
            PaymentMethod.CREDITO, "Crédito",
            PaymentMethod.PIX, "PIX",
            PaymentMethod.GATEWAY_PIX, "PIX online",
            PaymentMethod.MARCADO, "Marcado (fiado)");

    private final OperationalEmailDispatcher dispatcher;
    private final DevAlertPort devAlerts;
    private final PdvUseCase pdvUseCase;

    public OperationalNotificationListener(OperationalEmailDispatcher dispatcher, DevAlertPort devAlerts,
            PdvUseCase pdvUseCase) {
        this.dispatcher = dispatcher;
        this.devAlerts = devAlerts;
        this.pdvUseCase = pdvUseCase;
    }

    @EventListener
    @Async("taskExecutor")
    public void onAuditEvent(AuditEvent event) {
        try {
            handle(event);
        } catch (Exception ex) {
            log.error("notification.operational.event.failed type={} error={}", event.type(), ex.getMessage());
        }
    }

    void handle(AuditEvent event) {
        Map<String, Object> d = event.details();
        switch (event.type()) {
            case CASH_SESSION_OPENED -> dispatcher.toPermission(MANAGER_PERMISSION, NotificationType.CAIXA,
                    NotificationEmail.builder("caixa.abertura", "Caixa #" + d.get("sessionId") + " aberto por " + event.username())
                            .tone(Tone.INFO)
                            .section(null, List.of(
                                    Row.of("Operador", event.username()),
                                    Row.of("Aberto em", EmailFormat.dateTime(event.timestamp())),
                                    Row.of("Fundo de troco", EmailFormat.money(d.get("openingAmount"))),
                                    Row.of("Depósito", d.get("warehouseCode"))))
                            .action("Abrir PDV", CAIXA_PATH)
                            .build(),
                    event.username());
            case CASH_MOVEMENT_REGISTERED -> {
                boolean sangria = CashMovementType.SANGRIA.name().equals(String.valueOf(d.get("type")));
                String kind = sangria ? "Sangria" : "Suprimento";
                dispatcher.toPermission(MANAGER_PERMISSION, NotificationType.CAIXA,
                        NotificationEmail.builder("caixa.movimento", kind + " de " + EmailFormat.money(d.get("amount"))
                                        + " no caixa #" + d.get("sessionId"))
                                .tone(sangria ? Tone.WARNING : Tone.INFO)
                                .section(null, List.of(
                                        Row.of("Tipo", kind),
                                        Row.highlighted("Valor", EmailFormat.money(d.get("amount"))),
                                        Row.of("Motivo", d.get("reason")),
                                        Row.of("Operador", event.username()),
                                        Row.of("Quando", EmailFormat.dateTime(event.timestamp()))))
                                .action("Abrir PDV", CAIXA_PATH)
                                .build(),
                        event.username());
            }
            case CASH_SESSION_CLOSED -> dispatcher.toPermission(MANAGER_PERMISSION, NotificationType.CAIXA,
                    cashClosedEmail(event));
            case ORDER_CANCELLED, ORDER_REFUNDED -> {
                boolean refunded = event.type() == AuditEvent.EventType.ORDER_REFUNDED;
                String ref = orderRef(d);
                dispatcher.toPermission(MANAGER_PERMISSION, NotificationType.OPERACAO,
                        NotificationEmail.builder("operacao." + (refunded ? "reembolso" : "cancelamento"),
                                        "Pedido " + ref + (refunded ? " reembolsado" : " cancelado") + " por " + event.username())
                                .tone(Tone.WARNING)
                                .section(null, List.of(
                                        Row.of("Pedido", ref),
                                        Row.of("Status anterior", d.get("statusBefore")),
                                        Row.of("Motivo", blankToDash(d.get("reason"))),
                                        Row.of("Itens", d.get("skus")),
                                        Row.of("Feito por", event.username()),
                                        Row.of("Quando", EmailFormat.dateTime(event.timestamp()))))
                                .build(),
                        event.username());
            }
            case ORDER_PAYMENT_CORRECTED -> dispatcher.toPermission(MANAGER_PERMISSION, NotificationType.OPERACAO,
                    NotificationEmail.builder("operacao.correcao-pagamento",
                                    "Pagamento do pedido " + orderRef(d) + " corrigido por " + event.username())
                            .tone(Boolean.TRUE.equals(d.get("sessionWasClosed")) ? Tone.DANGER : Tone.WARNING)
                            .intro(Boolean.TRUE.equals(d.get("sessionWasClosed"))
                                    ? "A correção alterou um caixa que já estava fechado." : null)
                            .section(null, List.of(
                                    Row.of("Pedido", orderRef(d)),
                                    Row.of("Antes", d.get("before")),
                                    Row.of("Depois", d.get("after")),
                                    Row.of("Motivo", blankToDash(d.get("reason"))),
                                    Row.of("Feito por", event.username()),
                                    Row.of("Quando", EmailFormat.dateTime(event.timestamp()))))
                            .build(),
                    event.username());
            case PRODUCT_PRICE_CHANGED -> dispatcher.toPermission(MANAGER_PERMISSION, NotificationType.OPERACAO,
                    NotificationEmail.builder("operacao.preco", "Preço de " + d.get("sku") + " alterado para "
                                    + EmailFormat.money(d.get("effectivePrice")))
                            .section(null, List.of(
                                    Row.of("SKU", d.get("sku")),
                                    Row.highlighted("Novo preço", EmailFormat.money(d.get("effectivePrice"))),
                                    Row.of("Feito por", event.username()),
                                    Row.of("Quando", EmailFormat.dateTime(event.timestamp()))))
                            .action("Abrir estoque", "/app/estoque")
                            .build(),
                    event.username());
            case BUG_REPORT_CREATED -> devAlerts.alert("bug-report", String.valueOf(d.get("bugReportId")),
                    "Novo bug report: " + d.getOrDefault("title", "#" + d.get("bugReportId")),
                    devDetails(event, "bugReportId", "title", "description", "pageUrl", "userAgent"));
            case INTEGRATION_UPDATED -> devAlerts.alert("integracao", event.timestamp().toString(),
                    "Integração de " + d.get("integration") + " alterada por " + event.username(),
                    devDetails(event, "integration", "enabled", "provider", "fromEmail", "apiKeyChanged"));
            case PAYMENT_WEBHOOK_FAILED -> devAlerts.alert("webhook-pagamento",
                    d.get("provider") + "|" + d.get("exception"),
                    "Falha ao processar webhook de pagamento (" + d.get("provider") + ")",
                    devDetails(event, "provider", "orderNsu", "exception", "message"));
            case TOKEN_THEFT_DETECTED, ACCOUNT_LOCKED -> devAlerts.alert("seguranca",
                    event.type() + "|" + event.username(),
                    securityLabel(event.type()) + ": " + event.username(), devDetails(event));
            case DEV_ELEVATION_COMPLETED, USER_ROLE_ASSIGNED, USER_ROLE_REMOVED, ROLE_CREATED, ROLE_DELETED,
                    PERMISSION_CREATED, PERMISSION_DELETED, PERMISSION_ASSIGNED_TO_ROLE,
                    PERMISSION_REMOVED_FROM_ROLE -> devAlerts.alert("seguranca",
                    event.type() + "|" + event.username() + "|" + d,
                    securityLabel(event.type()) + " — " + event.username(), devDetails(event));
            default -> { }
        }
    }

    /**
     * Fechamento com o resumo do turno. Os totais vêm de {@link PdvUseCase#getSessionSummary} — o
     * mesmo cálculo da tela de fechamento (PDV-F038) —, não de uma conta refeita aqui.
     */
    private NotificationEmail cashClosedEmail(AuditEvent event) {
        Long sessionId = ((Number) event.details().get("sessionId")).longValue();
        CashRegisterSession session = pdvUseCase.getSession(sessionId);
        BigDecimal difference = session.differenceAmount();
        boolean diverges = difference != null && difference.signum() != 0;

        NotificationEmail.Builder email = NotificationEmail.builder("caixa.fechamento",
                        "Caixa #" + sessionId + " fechado" + (diverges ? " com diferença de " + EmailFormat.money(difference) : ""))
                .tone(diverges ? Tone.WARNING : Tone.SUCCESS)
                .intro(diverges ? "O valor contado não bate com o esperado." : "O valor contado bate com o esperado.")
                .section("Turno", List.of(
                        Row.of("Operador", session.operator()),
                        Row.of("Fechado por", session.closedBy()),
                        Row.of("Abertura", EmailFormat.dateTime(session.openedAt())),
                        Row.of("Fechamento", EmailFormat.dateTime(session.closedAt())),
                        Row.of("Observações", blankToDash(session.closingNotes()))))
                .section("Conferência da gaveta", List.of(
                        Row.of("Fundo de troco", EmailFormat.money(session.openingAmount())),
                        Row.of("Esperado", EmailFormat.money(session.expectedAmount())),
                        Row.of("Contado", EmailFormat.money(session.countedAmount())),
                        diverges ? Row.highlighted("Diferença", EmailFormat.money(difference))
                                : Row.of("Diferença", EmailFormat.money(BigDecimal.ZERO))));

        try {
            PdvUseCase.SessionSummary summary = pdvUseCase.getSessionSummary(sessionId);
            List<Row> totals = new ArrayList<>();
            for (PdvUseCase.PaymentTotal total : summary.totals()) {
                if (total.netAmount() != null && total.netAmount().signum() != 0) {
                    totals.add(Row.of(PAYMENT_LABELS.getOrDefault(total.method(), total.method().name()),
                            EmailFormat.money(total.netAmount())));
                }
            }
            totals.add(Row.highlighted("Total recebido", EmailFormat.money(summary.totalReceived())));
            if (summary.totalOnAccount() != null && summary.totalOnAccount().signum() != 0) {
                totals.add(Row.of("Vendido no marcado (fiado, não entrou no caixa)",
                        EmailFormat.money(summary.totalOnAccount())));
            }
            email.section("Vendas por forma de pagamento", totals);
        } catch (Exception ex) {
            log.warn("notification.cash-closed.summary.failed sessionId={} error={}", sessionId, ex.getMessage());
        }

        try {
            List<Row> movements = pdvUseCase.listCashMovements(sessionId, 0, MOVEMENTS_IN_SUMMARY).content().stream()
                    .map(OperationalNotificationListener::movementRow)
                    .toList();
            email.section("Sangrias e suprimentos", movements);
        } catch (Exception ex) {
            log.warn("notification.cash-closed.movements.failed sessionId={} error={}", sessionId, ex.getMessage());
        }

        return email.action("Abrir PDV", CAIXA_PATH).build();
    }

    private static Row movementRow(CashMovement m) {
        String label = (m.type() == CashMovementType.SANGRIA ? "Sangria" : "Suprimento")
                + " · " + EmailFormat.dateTime(m.createdAt())
                + (m.reason() == null || m.reason().isBlank() ? "" : " · " + m.reason());
        String value = (m.type() == CashMovementType.SANGRIA ? "-" : "+") + EmailFormat.money(m.amount());
        return Row.of(label, value);
    }

    private static Map<String, String> devDetails(AuditEvent event, String... keys) {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Usuário", event.username());
        rows.put("Quando", EmailFormat.dateTime(event.timestamp()));
        if (keys.length == 0) {
            event.details().forEach((k, v) -> rows.put(k, String.valueOf(v)));
        } else {
            for (String key : keys) {
                Object value = event.details().get(key);
                if (value != null) {
                    rows.put(key, String.valueOf(value));
                }
            }
        }
        return rows;
    }

    private static String securityLabel(AuditEvent.EventType type) {
        return switch (type) {
            case TOKEN_THEFT_DETECTED -> "Reuso de token detectado";
            case ACCOUNT_LOCKED -> "Conta bloqueada por tentativas de login";
            case DEV_ELEVATION_COMPLETED -> "Elevação DEV concluída";
            case USER_ROLE_ASSIGNED -> "Role atribuída";
            case USER_ROLE_REMOVED -> "Role removida";
            case ROLE_CREATED -> "Role criada";
            case ROLE_DELETED -> "Role excluída";
            case PERMISSION_CREATED -> "Permissão criada";
            case PERMISSION_DELETED -> "Permissão excluída";
            case PERMISSION_ASSIGNED_TO_ROLE -> "Permissão adicionada a role";
            case PERMISSION_REMOVED_FROM_ROLE -> "Permissão removida de role";
            default -> type.name();
        };
    }

    private static String orderRef(Map<String, Object> d) {
        Object number = d.get("orderNumber");
        return number != null && !"null".equals(String.valueOf(number)) ? String.valueOf(number) : "#" + d.get("orderId");
    }

    private static Object blankToDash(Object value) {
        return value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value)) ? "—" : value;
    }
}
