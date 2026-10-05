package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
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
            new BigDecimal("99.90"));
    private static final String API_URL = "https://api.resend.com/emails";

    @BeforeEach
    void setup() {
        renderer = mock(ThymeleafEmailRenderer.class);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(API_URL)
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ResendEmailAdapter(builder.build(), FROM, 15, "Confirme seu cadastro",
                "http://localhost:4200/auth/verify-email", renderer, new SimpleMeterRegistry());
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
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to[0]").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Recuperação de senha"))
                .andRespond(withSuccess());

        adapter.sendPasswordResetLink("user@example.com", "alice", "http://reset-link", 30);

        server.verify();
        verify(renderer).render(eq("password-reset"), argThat(m ->
                "alice".equals(m.get("username")) && "http://reset-link".equals(m.get("resetLink"))
                        && Long.valueOf(30).equals(m.get("ttlMinutes"))));
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
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Alerta de segurança: senha alterada"))
                .andRespond(withSuccess());

        adapter.sendPasswordChangedAlert("user@example.com", "alice",
                new SecurityEventContext(Instant.parse("2026-10-05T17:32:00Z"), "203.0.113.10", "Firefox"));

        server.verify();
        verify(renderer).render(eq("security-alert"), argThat((Map<String, Object> m) ->
                "alice".equals(m.get("username"))
                        && "05/10/2026 às 14:32".equals(m.get("occurredAt"))
                        && "203.0.113.10".equals(m.get("ip"))
                        && "Firefox".equals(m.get("device"))));
    }

    @Test
    void sendTokenTheftAlert_usa_template_security_alert() throws Exception {
        when(renderer.render(eq("security-alert"), anyMap())).thenReturn("<html>theft</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Alerta de segurança: acesso suspeito"))
                .andRespond(withSuccess());

        adapter.sendTokenTheftAlert("user@example.com", "alice", null);

        server.verify();
    }

    @Test
    void sendOrderConfirmation_envia_post_com_campos_corretos() throws Exception {
        when(renderer.render(eq("order-confirmation"), anyMap())).thenReturn("<html>confirmation</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.to[0]").value("customer@example.com"))
                .andExpect(jsonPath("$.subject").value("Recebemos seu pedido #7"))
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
                .andExpect(jsonPath("$.subject").value("Pagamento confirmado — pedido #7"))
                .andRespond(withSuccess());

        adapter.sendOrderStatusUpdate("customer@example.com", ORDER, "Pagamento confirmado");

        server.verify();
        verify(renderer).render(eq("order-status-update"), argThat((Map<String, Object> m) ->
                "Pagamento confirmado".equals(m.get("newStatusLabel"))));
    }

    @Test
    void sendOrderCancellation_usa_texto_de_reembolso_quando_refunded() throws Exception {
        when(renderer.render(eq("order-cancellation"), anyMap())).thenReturn("<html>cancel</html>");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.subject").value("Pedido #7 reembolsado"))
                .andRespond(withSuccess());

        adapter.sendOrderCancellation("customer@example.com", ORDER, "Produto com defeito", true);

        server.verify();
        verify(renderer).render(eq("order-cancellation"), argThat((Map<String, Object> m) ->
                Boolean.TRUE.equals(m.get("refunded")) && "Produto com defeito".equals(m.get("reason"))));
    }

    @Test
    void sendOrderCancellation_sem_motivo_nao_manda_o_texto_null() throws Exception {
        when(renderer.render(eq("order-cancellation"), anyMap())).thenReturn("<html>cancel</html>");
        server.expect(requestTo(API_URL)).andRespond(withSuccess());

        adapter.sendOrderCancellation("customer@example.com", ORDER, "  ", false);

        server.verify();
        verify(renderer).render(eq("order-cancellation"), argThat((Map<String, Object> m) ->
                m.containsKey("reason") && m.get("reason") == null));
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
        verify(devAlerts).emailDeliveryFailed(eq("email.welcome"), eq("user@example.com"), anyString());
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
        server.expect(requestTo(API_URL)).andRespond(withSuccess());

        adapter.sendOrderConfirmation("customer@example.com", ORDER, null);

        server.verify();
        verify(renderer).render(eq("order-confirmation"), argThat((Map<String, Object> m) ->
                m.containsKey("checkoutUrl") && m.get("checkoutUrl") == null));
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
                "contato@mahal.com", 15, "s", "http://front", renderer, new SimpleMeterRegistry());
        when(renderer.render(anyString(), anyMap())).thenReturn("<html/>");
        replyServer.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.from").value("Mahal <loja@mahal.com>"))
                .andExpect(jsonPath("$.reply_to").value("contato@mahal.com"))
                .andRespond(withSuccess());

        withReplyTo.sendTokenTheftAlert("user@example.com", "alice", null);

        replyServer.verify();
    }
}
