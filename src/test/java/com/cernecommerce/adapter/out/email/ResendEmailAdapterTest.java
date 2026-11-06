package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.model.ecommerce.EmailEncerramentoPedido;
import com.cernecommerce.core.domain.model.notification.AccountChange;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderClosureEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.notification.PurchaseReceiptEmail;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.ports.out.notification.DevAlertPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendEmailAdapterTest {

    private MockRestServiceServer server;
    private ResendEmailAdapter adapter;
    private ThymeleafEmailRenderer renderer;

    private static final String FROM = "noreply@test.com";
    private static final OrderEmailView ORDER = new OrderEmailView("Maria", "#7",
            List.of(new OrderEmailView.Line("Essência", BigDecimal.ONE, new BigDecimal("99.90"), new BigDecimal("99.90"))),
            new BigDecimal("99.90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("99.90"), 7L, false);
    private static final String API_URL = "https://api.resend.com/emails";

    @BeforeEach
    void setup() {
        renderer = mock(ThymeleafEmailRenderer.class);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(API_URL)
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ResendEmailAdapter(builder.build(), FROM, 15, "Confirme seu cadastro",
                "http://localhost:4200/auth/verify-email", "http://localhost:4200/auth/confirm-email-change", renderer,
                new SimpleMeterRegistry());
    }

    @Test
    void sendVerificationCode_envia_post_com_campos_corretos() throws Exception {
        when(renderer.render(eq("verification-code"), anyMap())).thenReturn("<html>code</html>");
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value(FROM))
                .andExpect(jsonPath("$.to[0]").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Confirme seu cadastro"))
                .andExpect(jsonPath("$.html").value("<html>code</html>"))
                .andRespond(withSuccess());

        adapter.sendVerificationCode("user@example.com", "alice", "ABC123");

        server.verify();
        verify(renderer).render(eq("verification-code"), argThat(m ->
                "alice".equals(m.get("username")) && "ABC123".equals(m.get("code"))));
    }

    @Test
    void sendPasswordResetLink_envia_post_com_campos_corretos() throws Exception {
        when(renderer.render(eq("password-reset"), anyMap())).thenReturn("<html>reset</html>");
        when(renderer.storeName()).thenReturn("Mahal");
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to[0]").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Redefina sua senha da Mahal"))
                .andRespond(withSuccess());

        adapter.sendPasswordResetLink("user@example.com", "alice", "http://reset-link", 30);

        server.verify();
        verify(renderer).render(eq("password-reset"), argThat(m ->
                "alice".equals(m.get("username")) && "http://reset-link".equals(m.get("resetLink"))
                        && Long.valueOf(30).equals(m.get("ttlMinutes"))));
    }

    @Test
    void sendCustomerNotice_com_whatsapp_leva_o_botao_para_a_loja() {
        when(renderer.render(eq("notification"), anyMap())).thenReturn("<html>fiado</html>");
        when(renderer.whatsappUrl("Oi!")).thenReturn("https://wa.me/5541999990000");
        server.expect(requestTo(API_URL)).andRespond(withSuccess());

        adapter.sendCustomerNotice("user@example.com", NotificationEmail.builder("fiado.criado", "Assunto")
                .intro("Intro").preheader("Prévia").whatsapp("Combinar", "Oi!").build());

        server.verify();
        verify(renderer).render(eq("notification"), argThat((Map<String, Object> m) ->
                "https://wa.me/5541999990000".equals(m.get("actionUrl")) && "Prévia".equals(m.get("preheader"))));
    }

    @Test
    void sendAccountChange_conta_desativada_tem_assunto_proprio() {
        when(renderer.render(eq("account-change"), anyMap())).thenReturn("<html>conta</html>");
        when(renderer.storeName()).thenReturn("Mahal");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Sua conta na Mahal foi desativada"))
                .andRespond(withSuccess());

        adapter.sendAccountChange("user@example.com", "alice", AccountChange.DISABLED, null);

        server.verify();
    }

    @Test
    void sendAccountChange_papel_cita_o_painel_no_assunto() {
        when(renderer.render(eq("account-change"), anyMap())).thenReturn("<html>conta</html>");
        when(renderer.storeName()).thenReturn("Mahal");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Seu acesso ao painel da Mahal mudou"))
                .andRespond(withSuccess());

        adapter.sendAccountChange("user@example.com", "alice", AccountChange.ROLE_REMOVED, "Atendente");

        server.verify();
        verify(renderer).render(eq("account-change"), argThat(m -> "ROLE_REMOVED".equals(m.get("change"))));
    }

    @Test
    void sendUserInvite_cita_a_loja_no_assunto() {
        when(renderer.render(eq("user-invite"), anyMap())).thenReturn("<html>convite</html>");
        when(renderer.storeName()).thenReturn("Mahal");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Seu acesso ao painel da Mahal"))
                .andRespond(withSuccess());

        adapter.sendUserInvite("novo@example.com", "alice", "Atendente", "http://convite", 48);

        server.verify();
        verify(renderer).render(eq("user-invite"), argThat(m -> "Atendente".equals(m.get("roles"))));
    }

    @Test
    void sendEmailChangeCode_leva_para_a_pagina_que_aplica_a_troca() {
        when(renderer.render(eq("email-change-request"), anyMap())).thenReturn("<html>troca</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.to[0]").value("novo@example.com"))
                .andExpect(jsonPath("$.subject").value("Confirme seu novo e-mail"))
                .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

        adapter.sendEmailChangeCode("novo@example.com", "alice", "ABC123");

        verify(renderer).render(eq("email-change-request"), argThat(m ->
                "http://localhost:4200/auth/confirm-email-change?code=ABC123".equals(m.get("confirmUrl"))));
        server.verify();
    }

    @Test
    void sendVerificationCode_lanca_EmailDeliveryException_quando_api_falha() {
        when(renderer.render(anyString(), anyMap())).thenReturn("<html/>");
        server.expect(requestTo(API_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.sendVerificationCode("user@example.com", "alice", "code"))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    void sendPasswordChangedAlert_usa_template_security_alert() throws Exception {
        when(renderer.render(eq("security-alert"), anyMap())).thenReturn("<html>alert</html>");
        when(renderer.storeName()).thenReturn("Mahal");
        when(renderer.appUrl("/auth/forgot-password")).thenReturn("http://painel/auth/forgot-password");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Sua senha da Mahal foi alterada"))
                .andRespond(withSuccess());

        adapter.sendPasswordChangedAlert("user@example.com", "alice",
                new SecurityEventContext(Instant.parse("2026-10-05T17:32:00Z"), "203.0.113.10", "Firefox"));

        server.verify();
        verify(renderer).render(eq("security-alert"), argThat((Map<String, Object> m) ->
                "alice".equals(m.get("username"))
                        && "05/10/2026 às 14:32".equals(m.get("occurredAt"))
                        && "203.0.113.10".equals(m.get("ip"))
                        && "Firefox".equals(m.get("device"))
                        && "http://painel/auth/forgot-password".equals(m.get("actionUrl"))));
    }

    @Test
    void sendTokenTheftAlert_usa_template_security_alert() throws Exception {
        when(renderer.render(eq("security-alert"), anyMap())).thenReturn("<html>theft</html>");
        when(renderer.storeName()).thenReturn("Mahal");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Desconectamos sua conta na Mahal por segurança"))
                .andRespond(withSuccess());

        adapter.sendTokenTheftAlert("user@example.com", "alice", null);

        server.verify();
        verify(renderer).render(eq("security-alert"), argThat((Map<String, Object> m) ->
                EmailTheme.DEFAULT.danger().equals(m.get("accent"))));
    }

    @Test
    void alerta_para_cliente_leva_a_recuperacao_para_o_app() {
        when(renderer.render(eq("security-alert"), anyMap())).thenReturn("<html>locked</html>");
        when(renderer.marketUrl("/conta")).thenReturn("http://loja/conta");
        server.expect(requestTo(API_URL)).andRespond(withSuccess());

        adapter.sendAccountLockedAlert("user@example.com", "Maria",
                new SecurityEventContext(Instant.parse("2026-10-05T17:32:00Z"), null, null).forCustomer());

        server.verify();
        verify(renderer).render(eq("security-alert"), argThat((Map<String, Object> m) ->
                "http://loja/conta".equals(m.get("actionUrl"))));
    }

    @Test
    void sendOrderConfirmation_envia_post_com_campos_corretos() throws Exception {
        when(renderer.render(eq("order-confirmation"), anyMap())).thenReturn("<html>confirmation</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.to[0]").value("customer@example.com"))
                .andExpect(jsonPath("$.subject").value("Falta só pagar o pedido #7"))
                .andRespond(withSuccess());

        adapter.sendOrderConfirmation("customer@example.com", ORDER, "http://checkout-url");

        server.verify();
        verify(renderer).render(eq("order-confirmation"), argThat((Map<String, Object> m) ->
                ORDER.equals(m.get("order")) && "http://checkout-url".equals(m.get("checkoutUrl"))));
    }

    @Test
    void sendOrderStatusUpdate_envia_post_com_campos_corretos() throws Exception {
        when(renderer.render(eq("order-status-update"), anyMap())).thenReturn("<html>status</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.to[0]").value("customer@example.com"))
                .andExpect(jsonPath("$.subject").value("Pedido #7 entregue"))
                .andRespond(withSuccess());
        OrderStageEmail stage = OrderStageEmail.builder("Pedido #7 entregue", "Chegou!", "Chegou!", "Entregue.").build();

        adapter.sendOrderStatusUpdate("customer@example.com", ORDER, stage);

        server.verify();
        verify(renderer).render(eq("order-status-update"), argThat((Map<String, Object> m) ->
                stage.equals(m.get("stage")) && "Chegou!".equals(m.get("preheader")) && m.get("actionUrl") == null));
    }

    @Test
    void sendOrderStatusUpdate_com_corrida_leva_o_botao_para_ela() throws Exception {
        when(renderer.render(eq("order-status-update"), anyMap())).thenReturn("<html>status</html>");
        server.expect(requestTo(API_URL)).andRespond(withSuccess());
        OrderStageEmail stage = OrderStageEmail.builder("s", "p", "t", "b").tracking().ride("https://corrida").build();

        adapter.sendOrderStatusUpdate("customer@example.com", ORDER, stage);

        server.verify();
        verify(renderer).render(eq("order-status-update"), argThat((Map<String, Object> m) ->
                "https://corrida".equals(m.get("actionUrl")) && "Acompanhar entrega".equals(m.get("actionLabel"))));
    }

    @Test
    void sendOrderStatusUpdate_com_acompanhamento_leva_ao_pedido_no_app() throws Exception {
        when(renderer.render(eq("order-status-update"), anyMap())).thenReturn("<html>status</html>");
        when(renderer.marketUrl("/conta/pedidos/7")).thenReturn("https://mahal.com/conta/pedidos/7");
        server.expect(requestTo(API_URL)).andRespond(withSuccess());
        OrderStageEmail stage = OrderStageEmail.builder("s", "p", "t", "b").tracking().build();

        adapter.sendOrderStatusUpdate("customer@example.com", ORDER, stage);

        server.verify();
        verify(renderer).render(eq("order-status-update"), argThat((Map<String, Object> m) ->
                "https://mahal.com/conta/pedidos/7".equals(m.get("actionUrl"))
                        && "Acompanhar pedido".equals(m.get("actionLabel"))));
    }

    @Test
    void sendOrderCancellation_com_devolucao_abre_o_whatsapp() throws Exception {
        when(renderer.render(eq("order-cancellation"), anyMap())).thenReturn("<html>cancel</html>");
        when(renderer.whatsappUrl("Oi! Quero combinar a devolução do pedido #7")).thenReturn("https://wa.me/5541999990000");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Reembolso do pedido #7"))
                .andRespond(withSuccess());
        OrderClosureEmail email = EmailEncerramentoPedido.reembolso(ORDER, new BigDecimal("99.90"),
                new BigDecimal("99.90"), false, false);

        adapter.sendOrderCancellation("customer@example.com", ORDER, email);

        server.verify();
        verify(renderer).render(eq("order-cancellation"), argThat((Map<String, Object> m) ->
                "https://wa.me/5541999990000".equals(m.get("actionUrl"))
                        && "Falar no WhatsApp".equals(m.get("actionLabel"))));
    }

    @Test
    void sendOrderCancellation_de_cancelamento_oferece_novo_pedido() throws Exception {
        when(renderer.render(eq("order-cancellation"), anyMap())).thenReturn("<html>cancel</html>");
        when(renderer.marketUrl("/")).thenReturn("https://mahal.com/");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Pedido #7 cancelado"))
                .andRespond(withSuccess());

        adapter.sendOrderCancellation("customer@example.com", ORDER, EmailEncerramentoPedido.pelaLoja(ORDER));

        server.verify();
        verify(renderer).render(eq("order-cancellation"), argThat((Map<String, Object> m) ->
                "https://mahal.com/".equals(m.get("actionUrl")) && "Fazer novo pedido".equals(m.get("actionLabel"))));
    }

    @Test
    void sendPurchaseReceipt_usa_o_numero_do_cupom_no_assunto() throws Exception {
        when(renderer.render(eq("purchase-receipt"), anyMap())).thenReturn("<html>receipt</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Comprovante da sua compra nº 000123"))
                .andRespond(withSuccess());
        PurchaseReceiptEmail receipt = new PurchaseReceiptEmail("nº 000123", "p", List.of(), List.of(), null, false);

        adapter.sendPurchaseReceipt("customer@example.com", ORDER, receipt);

        server.verify();
        verify(renderer).render(eq("purchase-receipt"), argThat((Map<String, Object> m) ->
                receipt.equals(m.get("receipt")) && "p".equals(m.get("preheader"))));
    }

    @Test
    void sendNotification_prefixa_o_assunto_com_a_loja_e_monta_a_url_do_painel() throws Exception {
        when(renderer.render(eq("notification"), anyMap())).thenReturn("<html>op</html>");
        when(renderer.storeName()).thenReturn("Mahal");
        when(renderer.appUrl("/app/pdv")).thenReturn("https://painel/app/pdv");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("[Mahal] Caixa #3 fechado"))
                .andRespond(withSuccess());

        NotificationEmail email = NotificationEmail.builder("caixa.fechamento", "Caixa #3 fechado")
                .tone(NotificationEmail.Tone.WARNING)
                .action("Abrir PDV", "/app/pdv")
                .build();
        adapter.sendNotification("gerente@example.com", email);

        server.verify();
        verify(renderer).render(eq("notification"), argThat((Map<String, Object> m) ->
                email.equals(m.get("email")) && "#b45309".equals(m.get("accent"))
                        && "https://painel/app/pdv".equals(m.get("actionUrl"))));
    }

    @Test
    void falha_de_entrega_avisa_os_devs_e_relanca() {
        DevAlertPort devAlerts = mock(DevAlertPort.class);
        adapter.reportFailuresTo(() -> devAlerts);
        when(renderer.render(anyString(), anyMap())).thenReturn("<html/>");
        server.expect(requestTo(API_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.sendWelcome("user@example.com", "alice"))
                .isInstanceOf(EmailDeliveryException.class);
        verify(devAlerts).emailDeliveryFailed(eq("email.welcome"), eq("user@example.com"), anyString(), anyString(),
                eq("<html/>"));
    }

    @Test
    void channelStatus_reportsConnectedToResend() {
        EmailChannelStatus status = adapter.channelStatus();

        assertThat(status.conectado()).isTrue();
        assertThat(status.provedor()).isEqualTo("RESEND");
    }
    @Test
    void sendOrderConfirmation_sem_checkoutUrl_ainda_envia() throws Exception {
        // Antes: Map.of com valor nulo lançava NPE e o e-mail sumia em silêncio.
        when(renderer.render(eq("order-confirmation"), anyMap())).thenReturn("<html>confirmation</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Pedido #7 na área!"))
                .andRespond(withSuccess());

        adapter.sendOrderConfirmation("customer@example.com", ORDER, null);

        server.verify();
        verify(renderer).render(eq("order-confirmation"), argThat((Map<String, Object> m) ->
                m.containsKey("checkoutUrl") && m.get("checkoutUrl") == null));
    }

    @Test
    void sendOrderConfirmation_monta_o_link_do_pedido_e_o_preheader() throws Exception {
        when(renderer.render(eq("order-confirmation"), anyMap())).thenReturn("<html>confirmation</html>");
        when(renderer.marketUrl("/conta/pedidos/7")).thenReturn("https://mahal.com/conta/pedidos/7");
        server.expect(requestTo(API_URL)).andRespond(withSuccess());

        adapter.sendOrderConfirmation("customer@example.com", ORDER, null);

        server.verify();
        verify(renderer).render(eq("order-confirmation"), argThat((Map<String, Object> m) ->
                "https://mahal.com/conta/pedidos/7".equals(m.get("trackingUrl"))
                        && "A loja confere tudo e te avisa assim que aceitar.".equals(m.get("preheader"))));
    }

    @Test
    void erro_do_resend_traz_o_corpo_da_resposta() {
        when(renderer.render(anyString(), anyMap())).thenReturn("<html/>");
        server.expect(requestTo(API_URL)).andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"validation_error\",\"message\":\"The mahal.com domain is not verified\"}"));

        assertThatThrownBy(() -> adapter.sendPasswordChangedAlert("user@example.com", "alice", null))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("domain is not verified");
    }

    @Test
    void reply_to_vai_no_corpo_quando_configurado() {
        RestClient.Builder builder = RestClient.builder().baseUrl(API_URL);
        MockRestServiceServer replyServer = MockRestServiceServer.bindTo(builder).build();
        ResendEmailAdapter withReplyTo = new ResendEmailAdapter(builder.build(), "Mahal <loja@mahal.com>",
                "contato@mahal.com", 15, "s", "http://front", "http://front/troca", renderer, new SimpleMeterRegistry());
        when(renderer.render(anyString(), anyMap())).thenReturn("<html/>");
        replyServer.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.from").value("Mahal <loja@mahal.com>"))
                .andExpect(jsonPath("$.reply_to").value("contato@mahal.com"))
                .andRespond(withSuccess());

        withReplyTo.sendTokenTheftAlert("user@example.com", "alice", null);

        replyServer.verify();
    }
}
