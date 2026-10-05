package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.out.notification.DevAlertPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OperationalNotificationListenerTest {

    private OperationalEmailDispatcher dispatcher;
    private DevAlertPort devAlerts;
    private PdvUseCase pdvUseCase;
    private OperationalNotificationListener listener;

    @BeforeEach
    void setUp() {
        dispatcher = mock(OperationalEmailDispatcher.class);
        devAlerts = mock(DevAlertPort.class);
        pdvUseCase = mock(PdvUseCase.class);
        listener = new OperationalNotificationListener(dispatcher, devAlerts, pdvUseCase);
    }

    @Test
    void abertura_de_caixa_vai_para_gestores_menos_quem_abriu() {
        listener.handle(AuditEvent.of(EventType.CASH_SESSION_OPENED, "atendente",
                Map.of("sessionId", 12L, "openingAmount", new BigDecimal("150.00"), "warehouseCode", "LOJA-01")));

        NotificationEmail email = captureManagerEmail(NotificationType.CAIXA, "atendente");
        assertThat(email.subject()).isEqualTo("Caixa #12 aberto por atendente");
        assertThat(rows(email)).anySatisfy(r -> assertThat(r.value()).matches("R\\$[\\s\\u00a0]150,00"));
    }

    @Test
    void sangria_sai_com_tom_de_alerta() {
        listener.handle(AuditEvent.of(EventType.CASH_MOVEMENT_REGISTERED, "atendente",
                Map.of("sessionId", 12L, "type", "SANGRIA", "amount", new BigDecimal("200"), "reason", "depósito")));

        NotificationEmail email = captureManagerEmail(NotificationType.CAIXA, "atendente");
        assertThat(email.tone()).isEqualTo(NotificationEmail.Tone.WARNING);
        assertThat(email.subject()).startsWith("Sangria de R$").endsWith("no caixa #12");
    }

    @Test
    void fechamento_com_diferenca_traz_resumo_por_forma_de_pagamento_e_movimentos() {
        Instant opened = Instant.parse("2026-10-05T12:00:00Z");
        when(pdvUseCase.getSession(12L)).thenReturn(new CashRegisterSession(12L, "atendente", opened,
                new BigDecimal("100"), "LOJA-01", opened.plusSeconds(8 * 3600), "admin",
                new BigDecimal("850"), new BigDecimal("840"), new BigDecimal("-10"),
                CashRegisterSession.Status.CLOSED, "faltou troco"));
        when(pdvUseCase.getSessionSummary(12L)).thenReturn(new PdvUseCase.SessionSummary(List.of(
                new PdvUseCase.PaymentTotal(PaymentMethod.DINHEIRO, new BigDecimal("350")),
                new PdvUseCase.PaymentTotal(PaymentMethod.PIX, new BigDecimal("420")),
                new PdvUseCase.PaymentTotal(PaymentMethod.CREDITO, BigDecimal.ZERO)),
                new BigDecimal("770"), new BigDecimal("60")));
        when(pdvUseCase.listCashMovements(12L, 0, 200)).thenReturn(new PageResult<>(List.of(
                new CashMovement(1L, 12L, CashMovementType.SANGRIA, new BigDecimal("200"), "depósito", "atendente", opened)),
                0, 200, 1, 1));

        listener.handle(AuditEvent.of(EventType.CASH_SESSION_CLOSED, "admin", Map.of("sessionId", 12L)));

        NotificationEmail email = captureManagerEmail(NotificationType.CAIXA, null);
        assertThat(email.tone()).isEqualTo(NotificationEmail.Tone.WARNING);
        assertThat(email.subject()).startsWith("Caixa #12 fechado com diferença de");
        assertThat(email.sections()).extracting(NotificationEmail.Section::title).containsExactly(
                "Turno", "Conferência da gaveta", "Vendas por forma de pagamento", "Sangrias e suprimentos");
        List<NotificationEmail.Row> totals = email.sections().get(2).rows();
        assertThat(totals).extracting(NotificationEmail.Row::label)
                .containsExactly("Dinheiro", "PIX", "Total recebido", "Vendido no marcado (fiado, não entrou no caixa)");
        assertThat(email.sections().get(1).rows()).filteredOn(NotificationEmail.Row::highlight)
                .extracting(NotificationEmail.Row::label).containsExactly("Diferença");
    }

    @Test
    void fechamento_sem_resumo_ainda_sai() {
        Instant opened = Instant.parse("2026-10-05T12:00:00Z");
        when(pdvUseCase.getSession(12L)).thenReturn(new CashRegisterSession(12L, "atendente", opened,
                new BigDecimal("100"), "LOJA-01", opened.plusSeconds(3600), "atendente",
                new BigDecimal("500"), new BigDecimal("500"), BigDecimal.ZERO, CashRegisterSession.Status.CLOSED, null));
        when(pdvUseCase.getSessionSummary(12L)).thenThrow(new IllegalStateException("db"));
        when(pdvUseCase.listCashMovements(12L, 0, 200)).thenThrow(new IllegalStateException("db"));

        listener.handle(AuditEvent.of(EventType.CASH_SESSION_CLOSED, "atendente", Map.of("sessionId", 12L)));

        NotificationEmail email = captureManagerEmail(NotificationType.CAIXA, null);
        assertThat(email.tone()).isEqualTo(NotificationEmail.Tone.SUCCESS);
        assertThat(email.subject()).isEqualTo("Caixa #12 fechado");
    }

    @Test
    void reembolso_vai_para_gestores_como_operacao() {
        listener.handle(AuditEvent.of(EventType.ORDER_REFUNDED, "admin",
                Map.of("orderId", 7L, "orderNumber", "V-0007", "statusBefore", "CONCLUIDO",
                        "reason", "defeito", "skus", List.of("ESS-1"))));

        NotificationEmail email = captureManagerEmail(NotificationType.OPERACAO, "admin");
        assertThat(email.subject()).isEqualTo("Pedido V-0007 reembolsado por admin");
    }

    @Test
    void bug_report_vira_alerta_de_dev() {
        listener.handle(AuditEvent.of(EventType.BUG_REPORT_CREATED, "atendente",
                Map.of("bugReportId", "5", "title", "Tela travou", "description", "ao fechar mesa")));

        verify(devAlerts).alert(eq("bug-report"), eq("5"), eq("Novo bug report: Tela travou"),
                argThat(d -> "ao fechar mesa".equals(d.get("description")) && "atendente".equals(d.get("Usuário"))));
    }

    @Test
    void falha_de_webhook_vira_alerta_de_dev_agrupado_por_provedor_e_excecao() {
        listener.handle(AuditEvent.of(EventType.PAYMENT_WEBHOOK_FAILED, "webhook:infinitepay",
                Map.of("provider", "infinitepay", "orderNsu", "9", "exception", "java.lang.IllegalStateException",
                        "message", "boom")));

        verify(devAlerts).alert(eq("webhook-pagamento"), eq("infinitepay|java.lang.IllegalStateException"),
                anyString(), anyMap());
    }

    @Test
    void evento_sem_interesse_operacional_e_ignorado() {
        listener.handle(AuditEvent.of(EventType.USER_LOGGED_IN, "alice"));

        verifyNoInteractions(dispatcher, devAlerts, pdvUseCase);
    }

    private NotificationEmail captureManagerEmail(NotificationType type, String exceptUsername) {
        ArgumentCaptor<NotificationEmail> captor = ArgumentCaptor.forClass(NotificationEmail.class);
        if (exceptUsername == null) {
            verify(dispatcher).toPermission(eq("FINANCEIRO_READ"), eq(type), captor.capture());
        } else {
            verify(dispatcher).toPermission(eq("FINANCEIRO_READ"), eq(type), captor.capture(), eq(exceptUsername));
        }
        return captor.getValue();
    }

    private static List<NotificationEmail.Row> rows(NotificationEmail email) {
        return email.sections().stream().flatMap(s -> s.rows().stream()).toList();
    }
}
