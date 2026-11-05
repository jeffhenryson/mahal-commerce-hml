package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.AccountChange;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.notification.OrderClosureEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
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
                eq("alice"), eq(NotificationType.ROLE_ASSIGNED), any(), contains("Administrador"));
        verify(emailPort, never()).sendAccountChange(any(), any(), any(), any());
    }

    @Test
    void role_assigned_com_email_ligado_envia_aviso_de_conta() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(
                List.of(new NotificationPreference("alice", NotificationType.ROLE_ASSIGNED, false, true)));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_ROLE_ASSIGNED, "alice",
                Map.of("role", "ROLE_ATENDENTE")));

        verify(emailPort).sendAccountChange("alice@example.com", "alice", AccountChange.ROLE_ASSIGNED, "Atendente");
    }

    @Test
    void conta_desativada_envia_aviso() {
        given(preferenceUseCase.getPreferences("alice")).willReturn(List.of());
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_DISABLED, "alice"));

        verify(emailPort).sendAccountChange("alice@example.com", "alice", AccountChange.DISABLED, null);
    }

    @Test
    void alerta_de_cliente_vai_pelo_primeiro_nome_e_marca_o_contexto() {
        User cliente = User.customer("customer@example.com", "hash", "customer@example.com", 42L, Set.of());
        given(preferenceUseCase.getPreferences("customer@example.com")).willReturn(List.of());
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("customer@example.com")).willReturn(Optional.of(cliente));
        given(customerRepository.findById(42L)).willReturn(Optional.of(stubCustomer()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ACCOUNT_LOCKED, "customer@example.com"));

        verify(emailPort).sendAccountLockedAlert(eq("customer@example.com"), eq("Cliente"),
                argThat(context -> context.customer()));
    }

    @Test
    void convite_aceito_nao_gera_alerta_de_senha() {
        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_INVITE_ACCEPTED, "alice"));

        verifyNoInteractions(emailPort);
    }

    @Test
    void conta_de_cliente_desativada_saudada_pelo_primeiro_nome() {
        User cliente = User.customer("customer@example.com", "hash", "customer@example.com", 42L, Set.of());
        given(preferenceUseCase.getPreferences("customer@example.com")).willReturn(List.of());
        given(notificationUseCase.notify(any(), any(), any(), any())).willReturn(SAVED);
        given(userRepository.findByUsername("customer@example.com")).willReturn(Optional.of(cliente));
        given(customerRepository.findById(42L)).willReturn(Optional.of(stubCustomer()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_DISABLED, "customer@example.com"));

        verify(emailPort).sendAccountChange("customer@example.com", "Cliente", AccountChange.DISABLED, null);
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
        assertThat(email.getValue().subject()).contains("80,00").contains("até 20/10/2026");
        assertThat(email.getValue().intro()).startsWith("Olá, Cliente! Registramos sua compra de");
        assertThat(email.getValue().sections()).extracting(NotificationEmail.Section::title)
                .containsExactly("Pagamento", null, "Itens");
        assertThat(email.getValue().sections().get(2).rows().get(0).label()).isEqualTo("2× Essência Menta");
        assertThat(email.getValue().whatsappMessage()).contains("80,00");
        assertThat(email.getValue().footnote()).contains("Não reconhece esta compra");
    }

    @Test
    void compra_com_parte_marcada_mostra_o_total_o_pago_e_o_cupom() {
        CustomerReceivable receivable = CustomerReceivable.open(42L, 7L, null, 3L, new BigDecimal("50.00"),
                LocalDate.of(2026, 10, 20), "atendente", Instant.now(), List.of());
        Order order = mock(Order.class);
        given(order.totalPayable()).willReturn(new BigDecimal("129.90"));
        given(order.orderNumber()).willReturn("000123");
        given(receivableUseCase.findByOrderId(7L)).willReturn(Optional.of(receivable));
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(customerRepository.findById(42L)).willReturn(Optional.of(stubCustomer()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.RECEIVABLE_CREATED, "atendente",
                Map.of("orderId", 7L, "customerId", 42L)));

        ArgumentCaptor<NotificationEmail> email = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(emailPort).sendCustomerNotice(eq("customer@example.com"), email.capture());
        assertThat(email.getValue().sections().get(0).rows()).extracting(NotificationEmail.Row::label)
                .containsExactly("Total da compra", "Pago na hora", "Valor marcado", "Vencimento", "Cupom");
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
        assertThat(email.getValue().intro()).isEqualTo("Olá, Cliente! Obrigado. Seu marcado está quitado.");
        assertThat(email.getValue().sections().get(0).rows())
                .anySatisfy(r -> assertThat(r.value()).isEqualTo("Nenhum"));
        assertThat(email.getValue().whatsappMessage()).isNull();
        assertThat(email.getValue().footnote()).contains("Não reconhece este pagamento");
    }

    @Test
    void pagamento_parcial_mostra_saldo_vencimento_e_como_pagar() {
        LocalDate due = LocalDate.now(EmailFormat.ZONE).plusDays(10);
        NotificationEmail email = pagamentoComSaldo(due);

        assertThat(email.preheader()).contains("30,00").contains("em aberto");
        assertThat(email.whatsappMessage()).contains("30,00");
        assertThat(email.sections().get(0).rows()).extracting(NotificationEmail.Row::label)
                .containsExactly("Valor pago", "Saldo em aberto", "Próximo vencimento", "Quando");
        assertThat(email.sections().get(1).text()).startsWith("Para pagar");
    }

    @Test
    void pagamento_parcial_com_saldo_vencido_diz_desde_quando() {
        NotificationEmail email = pagamentoComSaldo(LocalDate.of(2026, 1, 10));

        assertThat(email.sections().get(0).rows())
                .anySatisfy(r -> {
                    assertThat(r.label()).isEqualTo("Vencido desde");
                    assertThat(r.value()).isEqualTo("10/01/2026");
                });
    }

    private NotificationEmail pagamentoComSaldo(LocalDate due) {
        CustomerReceivable aberto = CustomerReceivable.open(42L, 7L, null, 3L, new BigDecimal("30.00"), due,
                "atendente", Instant.now(), List.of());
        given(customerRepository.findById(42L)).willReturn(Optional.of(stubCustomer()));
        given(receivableUseCase.listByCustomer(42L)).willReturn(List.of(
                new ReceivableUseCase.ReceivableView(aberto, null, null, null, 0, List.of())));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.RECEIVABLE_PAID, "atendente", Map.of(
                "customerId", 42L,
                "applied", List.of(Map.of("receivableId", 1L, "amount", new BigDecimal("50.00"), "statusAfter", "PARCIAL")),
                "openBalanceAfter", new BigDecimal("30.00"))));

        ArgumentCaptor<NotificationEmail> email = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(emailPort).sendCustomerNotice(eq("customer@example.com"), email.capture());
        return email.getValue();
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
                argThat(etapa -> "Pagamento do pedido #7 confirmado".equals(etapa.subject())));
    }

    @Test
    void aceite_do_pedido_do_app_nao_duplica_o_email_da_etapa() {
        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_STATUS_CHANGED, "caixa1",
                Map.of("orderId", 7L, "action", "ACCEPTED", "sessionId", 1L)));

        verifyNoInteractions(emailPort);
    }

    @Test
    void recusa_mostra_o_motivo_ao_cliente() {
        marketplaceOrder();

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_CANCELLED, "caixa1",
                Map.of("orderId", 7L, "origin", "PEDIDO_APP_RECUSADO", "reason", "Sem estoque")));

        verify(emailPort).sendOrderCancellation(eq("customer@example.com"),
                argThat(v -> "#7".equals(v.orderReference())),
                argThat(email -> email.subject().equals("Não conseguimos aceitar seu pedido #7")
                        && email.details().get(0).value().equals("Sem estoque")));
    }

    @Test
    void cancelamento_pela_loja_nao_mostra_o_motivo_digitado() {
        marketplaceOrder();

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_CANCELLED, "alice",
                Map.of("orderId", 7L, "reason", "Anotação interna")));

        verify(emailPort).sendOrderCancellation(eq("customer@example.com"), any(),
                argThat(email -> email.body().equals("A loja cancelou o pedido #7.") && email.details().isEmpty()));
    }

    @Test
    void cancelamento_pelo_cliente_confirma_o_pedido_dele() {
        marketplaceOrder();

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_CANCELLED, "maria",
                Map.of("orderId", 7L, "origin", "CLIENTE", "reason", "Mudei de ideia")));

        verify(emailPort).sendOrderCancellation(eq("customer@example.com"), any(),
                argThat(email -> email.body().contains("como você pediu") && email.details().isEmpty()));
    }

    @Test
    void reembolso_total_mostra_o_valor_e_nao_o_motivo() {
        marketplaceOrder();

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_REFUNDED, "alice",
                Map.of("orderId", 7L, "reason", "Anotação interna", "moneyRefunded", new BigDecimal("80.00"),
                        "onAccount", false, "partial", false, "refundedItems", List.of())));

        ArgumentCaptor<OrderClosureEmail> email = ArgumentCaptor.forClass(OrderClosureEmail.class);
        verify(emailPort).sendOrderCancellation(eq("customer@example.com"), any(), email.capture());
        assertThat(email.getValue().subject()).isEqualTo("Reembolso do pedido #7");
        assertThat(email.getValue().whatsapp()).isTrue();
        assertThat(email.getValue().details()).extracting(OrderStageEmail.Detail::value)
                .anySatisfy(v -> assertThat(v).contains("80,00"))
                .noneMatch(v -> v.contains("Anotação"));
    }

    @Test
    void reembolso_parcial_lista_so_o_item_devolvido() {
        OrderItem devolvido = mock(OrderItem.class);
        when(devolvido.id()).thenReturn(5L);
        when(devolvido.productName()).thenReturn("Carvão 1kg");
        OrderItem mantido = mock(OrderItem.class);
        when(mantido.id()).thenReturn(6L);
        when(mantido.productName()).thenReturn("Essência Menta");
        marketplaceOrder(List.of(devolvido, mantido));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_REFUNDED, "alice",
                Map.of("orderId", 7L, "reason", "x", "moneyRefunded", new BigDecimal("50.10"), "onAccount", false,
                        "partial", true, "refundedItems", List.of(Map.of("orderItemId", 5L,
                                "quantity", BigDecimal.ONE, "amount", new BigDecimal("50.10"))))));

        ArgumentCaptor<OrderEmailView> shown = ArgumentCaptor.forClass(OrderEmailView.class);
        ArgumentCaptor<OrderClosureEmail> email = ArgumentCaptor.forClass(OrderClosureEmail.class);
        verify(emailPort).sendOrderCancellation(eq("customer@example.com"), shown.capture(), email.capture());
        assertThat(shown.getValue().items()).extracting(OrderEmailView.Line::name).containsExactly("Carvão 1kg");
        assertThat(shown.getValue().total()).isEqualByComparingTo("50.10");
        assertThat(email.getValue().subject()).isEqualTo("Reembolso parcial do pedido #7");
        assertThat(email.getValue().totalLabel()).isEqualTo("Valor reembolsado");
    }

    @Test
    void reembolso_de_compra_marcada_cancela_a_divida_sem_falar_em_devolucao() {
        marketplaceOrder();

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.ORDER_REFUNDED, "alice",
                Map.of("orderId", 7L, "reason", "x", "moneyRefunded", BigDecimal.ZERO, "onAccount", true,
                        "partial", false, "refundedItems", List.of())));

        verify(emailPort).sendOrderCancellation(eq("customer@example.com"), any(),
                argThat(email -> email.title().equals("Sua compra marcada foi cancelada") && !email.whatsapp()));
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
    void usuario_criado_com_senha_recebe_boas_vindas_sem_depender_de_preferencia() {
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(stubUser()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_CREATED, "alice", Map.of("invited", false)));

        verify(emailPort).sendWelcome("alice@example.com", "alice");
        verifyNoInteractions(preferenceUseCase);
    }

    @Test
    void email_verificado_nao_repete_as_boas_vindas() {
        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.USER_EMAIL_VERIFIED, "alice"));

        verifyNoInteractions(emailPort);
    }

    @Test
    void cliente_cadastrado_no_app_recebe_boas_vindas_pelo_primeiro_nome() {
        given(customerRepository.findById(42L)).willReturn(Optional.of(stubCustomer()));

        listener.onAuditEvent(AuditEvent.of(AuditEvent.EventType.CUSTOMER_MARKETPLACE_REGISTERED,
                "customer@example.com", Map.of("customerId", 42L)));

        verify(emailPort).sendCustomerWelcome("customer@example.com", "Cliente");
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
        marketplaceOrder(List.of());
    }

    private void marketplaceOrder(List<OrderItem> items) {
        Order order = mock(Order.class);
        when(order.channel()).thenReturn(SalesChannel.MARKETPLACE);
        when(order.customerId()).thenReturn(42L);
        when(order.id()).thenReturn(7L);
        when(order.items()).thenReturn(items);
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
