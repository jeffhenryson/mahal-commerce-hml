package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.config.WhatsappIntegrationSettings;
import com.cernecommerce.core.domain.model.config.WhatsappConnectionStatus;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.AutomationPlatformSettings;
import com.cernecommerce.core.domain.model.config.AutomationPlatform;
import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.email.InvalidEmailIntegrationException;
import com.cernecommerce.core.domain.model.config.EmailIntegrationSettings;
import com.cernecommerce.core.domain.model.config.EmailProvider;
import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.config.EmailTestResult;
import com.cernecommerce.core.domain.model.config.EmailTestTarget;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StoreIntegrationControllerTest {

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());
    private static final EmailIntegrationSettings SETTINGS = new EmailIntegrationSettings(true, EmailProvider.RESEND,
            "loja@mahal.com", "Mahal", null, "ABCD", Instant.parse("2026-10-05T12:00:00Z"), "admin");

    private MockMvc mockMvc;
    private EmailIntegrationUseCase useCase;
    private WhatsappIntegrationUseCase whatsapp;
    private AutomationPlatformIntegrationUseCase platform;
    private ApplicationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        useCase = mock(EmailIntegrationUseCase.class);
        whatsapp = mock(WhatsappIntegrationUseCase.class);
        platform = mock(AutomationPlatformIntegrationUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        when(useCase.activeChannel()).thenReturn(EmailChannelStatus.of(true, "RESEND", "loja"));
        when(useCase.environmentChannel()).thenReturn(EmailChannelStatus.of(true, "MAILPIT", "hml"));
        mockMvc = controller("http://localhost:8025");
    }

    private MockMvc controller(String mailpitUiUrl) {
        return MockMvcBuilders
                .standaloneSetup(new StoreIntegrationController(useCase, whatsapp, platform, publisher, mailpitUiUrl,
                        "https://api.mahal.com/webhooks/whatsapp"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void get_nunca_devolve_a_chave() throws Exception {
        when(useCase.get()).thenReturn(SETTINGS);

        mockMvc.perform(get("/store/integrations/email").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.apiKeyLast4").value("ABCD"))
                .andExpect(jsonPath("$.activeProvider").value("RESEND"))
                .andExpect(jsonPath("$.environmentProvider").value("MAILPIT"))
                .andExpect(jsonPath("$.mailpitUiUrl").value("http://localhost:8025"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void get_sem_mailpit_no_ambiente_nao_expoe_a_caixa_de_teste() throws Exception {
        when(useCase.get()).thenReturn(SETTINGS);
        when(useCase.environmentChannel()).thenReturn(EmailChannelStatus.of(true, "RESEND", "prod"));

        mockMvc.perform(get("/store/integrations/email").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environmentProvider").value("RESEND"))
                .andExpect(jsonPath("$.mailpitUiUrl").doesNotExist());
    }

    @Test
    void get_mailpit_sem_url_configurada_nao_expoe_a_caixa_de_teste() throws Exception {
        when(useCase.get()).thenReturn(SETTINGS);

        controller(" ").perform(get("/store/integrations/email").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mailpitUiUrl").doesNotExist());
    }

    @Test
    void put_repassa_comando_e_audita_sem_a_chave() throws Exception {
        when(useCase.update(any(), eq("admin"))).thenReturn(SETTINGS);

        mockMvc.perform(put("/store/integrations/email").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"fromEmail\":\"loja@mahal.com\",\"fromName\":\"Mahal\","
                                + "\"apiKey\":\"re_super_secret\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("re_super_secret"))));

        ArgumentCaptor<EmailIntegrationUseCase.UpdateCommand> cmd =
                ArgumentCaptor.forClass(EmailIntegrationUseCase.UpdateCommand.class);
        verify(useCase).update(cmd.capture(), eq("admin"));
        assertThat(cmd.getValue().apiKey()).isEqualTo("re_super_secret");
        assertThat(cmd.getValue().enabled()).isTrue();

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(AuditEvent.EventType.INTEGRATION_UPDATED);
        assertThat(event.getValue().details().toString()).doesNotContain("re_super_secret");
        assertThat(event.getValue().details()).containsEntry("apiKeyChanged", true);
    }

    @Test
    void put_invalido_retorna_422_com_a_mensagem() throws Exception {
        when(useCase.update(any(), any())).thenThrow(
                new InvalidEmailIntegrationException("Para ativar a integração informe a chave da API"));

        mockMvc.perform(put("/store/integrations/email").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_EMAIL_INTEGRATION"));
    }

    @Test
    void test_devolve_resultado_por_email() throws Exception {
        when(useCase.sendTest("eu@x.com", EmailSample.PASSWORD_RESET, null, "admin"))
                .thenReturn(List.of(EmailTestResult.failed(EmailSample.PASSWORD_RESET, "403 domain not verified")));

        mockMvc.perform(post("/store/integrations/email/test").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"eu@x.com\",\"sample\":\"PASSWORD_RESET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sample").value("PASSWORD_RESET"))
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].error").value("403 domain not verified"));
    }

    @Test
    void test_repassa_o_alvo_ambiente() throws Exception {
        when(useCase.sendTest("eu@x.com", null, EmailTestTarget.ENVIRONMENT, "admin"))
                .thenReturn(List.of(EmailTestResult.ok(EmailSample.VERIFICATION_CODE)));

        mockMvc.perform(post("/store/integrations/email/test").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"eu@x.com\",\"target\":\"ENVIRONMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    void test_sem_destinatario_valido_retorna_400() throws Exception {
        mockMvc.perform(post("/store/integrations/email/test").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"nao-email\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }

    // ── WhatsApp ──────────────────────────────────────────────────────────────

    @Test
    void whatsapp_get_nuncaDevolveOsTokens_eTrazCallbackEStatus() throws Exception {
        when(whatsapp.get()).thenReturn(new WhatsappIntegrationSettings(true, "1234567890", "9876543210", "a1b2",
                true, Instant.parse("2026-10-07T17:20:00Z"), "admin"));
        when(whatsapp.connectionStatus()).thenReturn(WhatsappConnectionStatus.up());

        mockMvc.perform(get("/store/integrations/whatsapp").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTokenConfigured").value(true))
                .andExpect(jsonPath("$.accessTokenLast4").value("a1b2"))
                .andExpect(jsonPath("$.verifyTokenConfigured").value(true))
                .andExpect(jsonPath("$.callbackUrl").value("https://api.mahal.com/webhooks/whatsapp"))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.verifyToken").doesNotExist());
    }

    @Test
    void whatsapp_put_repassaComando_eAuditaSemOsTokens() throws Exception {
        when(whatsapp.update(any(), eq("admin"))).thenReturn(new WhatsappIntegrationSettings(true, "1234567890", null,
                "wxyz", false, Instant.now(), "admin"));
        when(whatsapp.connectionStatus()).thenReturn(WhatsappConnectionStatus.disconnected("token inválido"));

        mockMvc.perform(put("/store/integrations/whatsapp").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"phoneNumberId\":\"1234567890\",\"accessToken\":\"EAAG-segredo-wxyz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusDetail").value("token inválido"))
                .andExpect(content().string(not(containsString("EAAG-segredo"))));

        verify(whatsapp).update(argThat(c -> c.enabled() && "EAAG-segredo-wxyz".equals(c.accessToken())
                && c.verifyToken() == null), eq("admin"));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        AuditEvent audit = (AuditEvent) event.getValue();
        assertThat(audit.type()).isEqualTo(AuditEvent.EventType.INTEGRATION_UPDATED);
        assertThat(audit.details()).containsEntry("integration", "whatsapp").containsEntry("accessTokenChanged", true)
                .containsEntry("verifyTokenChanged", false);
        assertThat(audit.details().toString()).doesNotContain("EAAG-segredo");
    }

    @Test
    void whatsapp_put_invalido_retorna400() throws Exception {
        when(whatsapp.update(any(), any())).thenThrow(new InvalidIntegrationException("Para ativar informe o Phone Number ID"));

        mockMvc.perform(put("/store/integrations/whatsapp").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INTEGRATION"));
    }

    @Test
    void whatsapp_test_recusaDaMeta_volta200ComErro() throws Exception {
        when(whatsapp.sendTest("5585999999999", null, "admin"))
                .thenReturn(IntegrationTestResult.failed("Recipient not in allowed list", null));

        mockMvc.perform(post("/store/integrations/whatsapp/test").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"5585999999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Recipient not in allowed list"));
    }

    // ── Plataforma de automação ───────────────────────────────────────────────

    @Test
    void plataforma_get_e_put() throws Exception {
        AutomationPlatformSettings settings = new AutomationPlatformSettings(true, AutomationPlatform.MAKE,
                "https://hook.make.com", "wxyz", Instant.now(), "admin");
        when(platform.get()).thenReturn(settings);
        when(platform.update(any(), eq("admin"))).thenReturn(settings);

        mockMvc.perform(get("/store/integrations/automation-platform").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("MAKE"))
                .andExpect(jsonPath("$.tokenConfigured").value(true))
                .andExpect(jsonPath("$.tokenLast4").value("wxyz"))
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(put("/store/integrations/automation-platform").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"platform\":\"MAKE\",\"baseUrl\":\"https://hook.make.com\"}"))
                .andExpect(status().isOk());

        verify(platform).update(argThat(c -> c.platform() == AutomationPlatform.MAKE && c.token() == null), eq("admin"));
        verify(publisher).publishEvent(argThat((Object e) -> e instanceof AuditEvent ev
                && "automation-platform".equals(ev.details().get("integration"))
                && Boolean.FALSE.equals(ev.details().get("tokenChanged"))));
    }

    @Test
    void plataforma_test_devolveStatusHttp() throws Exception {
        when(platform.sendTest("admin")).thenReturn(IntegrationTestResult.ok("HTTP 200"));

        mockMvc.perform(post("/store/integrations/automation-platform/test").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.detail").value("HTTP 200"));
    }
}
