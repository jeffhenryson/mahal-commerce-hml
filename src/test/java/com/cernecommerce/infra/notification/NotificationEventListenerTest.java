package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.ReceivableItem;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class NotificationEventListenerTest {

    @Mock NotificationUseCase notificationUseCase;
    @Mock NotificationPreferenceUseCase preferenceUseCase;
    @Mock UserRepository userRepository;
    @Mock EmailPort emailPort;
    @Mock NotificationSsePort ssePort;
    @Mock OrderRepository orderRepository;
    @Mock CustomerRepository customerRepository;
    @Mock ReceivableUseCase receivableUseCase;

    NotificationEventListener listener;

    private static final Notification SAVED = new Notification(
            1L, "alice", NotificationType.PASSWORD_CHANGED, "Senha alterada", "...", null, Instant.now());

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Executor síncrono: o teste verifica o que é enviado, não a thread.
        listener = new NotificationEventListener(notificationUseCase,
                new OperationalEmailDispatcher(preferenceUseCase, userRepository, emailPort),
                userRepository, emailPort, ssePort, orderRepository, customerRepository, receivableUseCase, Runnable::run);
    }

    @Test
    void password_changed_persiste_envia_sse_e_email() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(todosHabilitados("alice"));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_PASSWORD_CHANGED, "alice"));

        verify(notificationUseCase).notify(eq("alice"), eq(NotificationType.PASSWORD_CHANGED), any(), any());
        verify(ssePort).send(eq("alice"), eq(SAVED));
        verify(emailPort).sendPasswordChangedAlert(eq("alice@example.com"), eq("alice"), any());
    }

    @Test
    void inapp_desabilitado_pula_persistencia_e_sse_mas_envia_email() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(
                List.of(new NotificationPreference("alice", NotificationType.PASSWORD_CHANGED, false, true)));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_PASSWORD_CHANGED, "alice"));

        verify(notificationUseCase, never()).notify(any(), any(), any(), any());
        verify(ssePort, never()).send(any(), any());
        verify(emailPort).sendPasswordChangedAlert(eq("alice@example.com"), eq("alice"), any());
    }

    @Test
    void email_desabilitado_persiste_e_envia_sse_mas_pula_email() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(
                List.of(new NotificationPreference("alice", NotificationType.PASSWORD_CHANGED, true, false)));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_PASSWORD_CHANGED, "alice"));

        verify(notificationUseCase).notify(eq("alice"), eq(NotificationType.PASSWORD_CHANGED), any(), any());
        verify(ssePort).send(eq("alice"), eq(SAVED));
        verify(emailPort, never()).sendPasswordChangedAlert(any(), any(), any());
    }

    @Test
    void falha_na_lookup_de_preferencia_usa_defaults_todos_habilitados() {
        given(preferenceUseCase.getPreferences("alice")).willThrow(new RuntimeException("db error"));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_PASSWORD_CHANGED, "alice"));

        verify(notificationUseCase).notify(eq("alice"), eq(NotificationType.PASSWORD_CHANGED), any(), any());
        verify(emailPort).sendPasswordChangedAlert(any(), any(), any());
    }

    @Test
    void role_assigned_inclui_nome_do_role_no_corpo() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(
                List.of(new NotificationPreference("alice", NotificationType.ROLE_ASSIGNED, true, false)));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_ROLE_ASSIGNED, "alice",
                Map.of("role", "ROLE_ADMIN")));

        verify(notificationUseCase).notify(
                eq("alice"), eq(NotificationType.ROLE_ASSIGNED), any(), contains("ROLE_ADMIN"));
        verify(emailPort, never()).sendAccountChange(any(), any(), any(), any());
    }

    @Test
    void role_assigned_com_email_ligado_envia_aviso_de_conta() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(
                List.of(new NotificationPreference("alice", NotificationType.ROLE_ASSIGNED, false, true)));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_ROLE_ASSIGNED, "alice",
                Map.of("role", "ROLE_ATENDENTE")));

        verify(emailPort).sendAccountChange(eq("alice@example.com"), eq("alice"), eq("Papel atribuído"),
                contains("ROLE_ATENDENTE"));
    }

    @Test
    void conta_desativada_envia_aviso() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(List.of());
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_DISABLED, "alice"));

        verify(emailPort).sendAccountChange(eq("alice@example.com"), eq("alice"), eq("Conta desativada"), any());
    }

    @Test
    void troca_de_email_confirmada_vai_para_o_email_atual_do_usuario() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(List.of());
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.EMAIL_CHANGE_CONFIRMED, "alice"));

        verify(emailPort).sendEmailChangeConfirmed("alice@example.com", "alice");
    }

    @Test
    void senha_redefinida_e_google_vinculado_viram_alerta_de_seguranca() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(List.of());
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.PASSWORD_RESET_COMPLETED, "alice"));
        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.OAUTH_GOOGLE_LINKED, "alice"));

        verify(emailPort).sendPasswordResetAlert(eq("alice@example.com"), eq("alice"), any());
        verify(emailPort).sendGoogleLinkedAlert(eq("alice@example.com"), eq("alice"), any());
    }

    @Test
    void compra_marcada_envia_comprovante_de_fiado_ao_cliente() {
        CustomerReceivable receivable = CustomerReceivable.open(42L, 7L, null, 3L, new BigDecimal("80.00"),
                LocalDate.of(2026, 10, 20), "atendente", Instant.now(), List.of(
                        new ReceivableItem(1L, 1L, "ESS-1", "Essência Menta", new BigDecimal("2"), new BigDecimal("80.00"), "NORMAL")));
        given(receivableUseCase.findByOrderId(7L)).willReturn(Optional.of(receivable));
        given(customerRepository.findById(42L)).willReturn(Optional.of(stubCustomer()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.RECEIVABLE_CREATED, "atendente",
                Map.of("orderId", 7L, "customerId", 42L)));

        ArgumentCaptor<NotificationEmail> email = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(emailPort).sendCustomerNotice(eq("customer@example.com"), email.capture());
        assertThat(email.getValue().subject()).contains("80,00").contains("20/10/2026");
        assertThat(email.getValue().sections().get(0).rows().get(0).label()).isEqualTo("2× Essência Menta");
    }

    @Test
    void pagamento_de_fiado_confirma_valor_e_saldo() {
        given(customerRepository.findById(42L)).willReturn(Optional.of(stubCustomer()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.RECEIVABLE_PAID, "atendente", Map.of(
                "customerId", 42L,
                "applied", List.of(Map.of("receivableId", 1L, "amount", new BigDecimal("50.00"), "statusAfter", "PARCIAL"),
                        Map.of("receivableId", 2L, "amount", new BigDecimal("30.00"), "statusAfter", "QUITADO")),
                "openBalanceAfter", BigDecimal.ZERO)));

        ArgumentCaptor<NotificationEmail> email = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(emailPort).sendCustomerNotice(eq("customer@example.com"), email.capture());
        assertThat(email.getValue().subject()).contains("80,00");
        assertThat(email.getValue().sections().get(0).rows())
                .anySatisfy(r -> assertThat(r.value()).isEqualTo("Nada — tudo quitado"));
    }

    @Test
    void tipo_de_evento_nao_mapeado_e_ignorado() {
        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_LOGGED_IN, "alice"));

        verifyNoInteractions(notificationUseCase, ssePort, emailPort);
    }

    @Test
    void account_locked_persiste_e_envia_email() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(todosHabilitados("alice"));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ACCOUNT_LOCKED, "alice"));

        verify(notificationUseCase).notify(eq("alice"), eq(NotificationType.ACCOUNT_LOCKED), any(), any());
        verify(emailPort).sendAccountLockedAlert(eq("alice@example.com"), eq("alice"), any());
    }

    @Test
    void order_status_changed_envia_email_de_atualizacao_para_o_cliente_do_pedido() {
        marketplaceOrder();

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_STATUS_CHANGED, "webhook",
                Map.of("orderId", 7L, "to", "PAGO")));

        verify(emailPort).sendOrderStatusUpdate(eq("customer@example.com"),
                argThat(v -> "Cliente Exemplo".equals(v.customerName()) && "#7".equals(v.orderReference())),
                eq("Pagamento confirmado"));
    }

    @Test
    void order_cancelled_envia_email_de_cancelamento_com_motivo() {
        marketplaceOrder();

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_CANCELLED, "alice",
                Map.of("orderId", 7L, "reason", "Cliente desistiu")));

        verify(emailPort).sendOrderCancellation(eq("customer@example.com"),
                argThat(v -> "#7".equals(v.orderReference())), eq("Cliente desistiu"), eq(false));
    }

    @Test
    void order_refunded_envia_email_de_reembolso() {
        marketplaceOrder();

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_REFUNDED, "alice",
                Map.of("orderId", 7L, "reason", "Produto com defeito")));

        verify(emailPort).sendOrderCancellation(eq("customer@example.com"), any(), eq("Produto com defeito"), eq(true));
    }

    @Test
    void order_cancelled_sem_motivo_passa_null_e_nao_o_texto_null() {
        marketplaceOrder();
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("orderId", 7L);
        details.put("reason", null);

        listener.onAuditEvent(new AuditEvent(AuditEvent.EventType.ORDER_CANCELLED, "alice", Instant.now(), details));

        verify(emailPort).sendOrderCancellation(eq("customer@example.com"), any(), isNull(), eq(false));
    }

    @Test
    void pedido_de_balcao_com_cliente_nao_recebe_email_de_status() {
        Order order = mock(Order.class);
        when(order.channel()).thenReturn(SalesChannel.BALCAO);
        when(order.customerId()).thenReturn(42L);
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_STATUS_CHANGED, "alice",
                Map.of("orderId", 7L, "to", "CONCLUIDO")));

        verifyNoInteractions(emailPort, customerRepository);
    }

    @Test
    void email_verificado_envia_boas_vindas_sem_depender_de_preferencia() {
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_EMAIL_VERIFIED, "alice"));

        verify(emailPort).sendWelcome("alice@example.com", "alice");
        verifyNoInteractions(preferenceUseCase);
    }

    @Test
    void alerta_de_seguranca_leva_ip_e_user_agent_da_requisicao() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(todosHabilitados("alice"));
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("User-Agent", "Firefox");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            AuditEvent event = AuditEvent.of(AuditEvent.EventType.ACCOUNT_LOCKED, "alice");
            listener.onAuditEvent(event);

            verify(emailPort).sendAccountLockedAlert("alice@example.com", "alice",
                    new SecurityEventContext(event.timestamp(), "203.0.113.10", "Firefox"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void order_status_changed_sem_customerId_nao_envia_email_venda_de_balcao() {
        Order order = mock(Order.class);
        when(order.customerId()).thenReturn(null);
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_STATUS_CHANGED, "alice",
                Map.of("orderId", 7L, "to", "ENVIADO")));

        verifyNoInteractions(emailPort);
        verifyNoInteractions(customerRepository);
    }

    // helpers

    private void marketplaceOrder() {
        Order order = mock(Order.class);
        when(order.channel()).thenReturn(SalesChannel.MARKETPLACE);
        when(order.customerId()).thenReturn(42L);
        when(order.id()).thenReturn(7L);
        when(order.items()).thenReturn(List.of());
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(customerRepository.findById(42L)).willReturn(Optional.of(stubCustomer()));
    }

    private Customer stubCustomer() {
        return new Customer(42L, "Cliente Exemplo", "11999999999", "customer@example.com",
                null, "MARKETPLACE", Instant.now(), com.cernecommerce.core.domain.model.crm.CustomerStage.NOVO_LEAD);
    }

    private List<NotificationPreference> todosHabilitados(String username) {
        return List.of(
                new NotificationPreference(username, NotificationType.PASSWORD_CHANGED, true, true),
                new NotificationPreference(username, NotificationType.ACCOUNT_LOCKED, true, true),
                new NotificationPreference(username, NotificationType.ROLE_ASSIGNED, true, true));
    }

    private User stubUser() {
        return User.ofPendingVerification("alice", "hash", "alice@example.com", Set.of());
    }
}
