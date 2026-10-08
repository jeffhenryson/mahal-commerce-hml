package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.AutomationPlatformIntegrationRequest;
import com.cernecommerce.adapter.in.dtos.request.EmailIntegrationRequest;
import com.cernecommerce.adapter.in.dtos.request.EmailIntegrationTestRequest;
import com.cernecommerce.adapter.in.dtos.request.WhatsappIntegrationRequest;
import com.cernecommerce.adapter.in.dtos.request.WhatsappTestRequest;
import com.cernecommerce.adapter.in.dtos.response.AutomationPlatformIntegrationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.EmailIntegrationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.EmailTestResultDTO;
import com.cernecommerce.adapter.in.dtos.response.IntegrationTestResultDTO;
import com.cernecommerce.adapter.in.dtos.response.WhatsappIntegrationResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.config.AutomationPlatformSettings;
import com.cernecommerce.core.domain.model.config.EmailIntegrationSettings;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.WhatsappConnectionStatus;
import com.cernecommerce.core.domain.model.config.WhatsappIntegrationSettings;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/store/integrations")
@Tag(name = "Store Integrations", description = "Tokens de integração da loja (e-mail/Resend, WhatsApp, n8n/Make)")
public class StoreIntegrationController {

    private final EmailIntegrationUseCase emailIntegrationUseCase;
    private final WhatsappIntegrationUseCase whatsappIntegrationUseCase;
    private final AutomationPlatformIntegrationUseCase automationPlatformIntegrationUseCase;
    private final ApplicationEventPublisher publisher;
    private final String mailpitUiUrl;
    private final String whatsappCallbackUrl;

    public StoreIntegrationController(EmailIntegrationUseCase emailIntegrationUseCase,
            WhatsappIntegrationUseCase whatsappIntegrationUseCase,
            AutomationPlatformIntegrationUseCase automationPlatformIntegrationUseCase,
            ApplicationEventPublisher publisher, @Value("${mailpit.ui-url:}") String mailpitUiUrl,
            @Value("${whatsapp.webhook-url:}") String whatsappCallbackUrl) {
        this.emailIntegrationUseCase = emailIntegrationUseCase;
        this.whatsappIntegrationUseCase = whatsappIntegrationUseCase;
        this.automationPlatformIntegrationUseCase = automationPlatformIntegrationUseCase;
        this.publisher = publisher;
        this.mailpitUiUrl = mailpitUiUrl == null || mailpitUiUrl.isBlank() ? null : mailpitUiUrl.strip();
        this.whatsappCallbackUrl = whatsappCallbackUrl == null || whatsappCallbackUrl.isBlank()
                ? null : whatsappCallbackUrl.strip();
    }

    @Operation(summary = "Integração de e-mail", description = "Nunca devolve a chave da API.")
    @GetMapping("/email")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<EmailIntegrationResponseDTO> getEmail() {
        return ResponseEntity.ok(toResponse(emailIntegrationUseCase.get()));
    }

    @Operation(summary = "Atualiza a integração de e-mail",
            description = "Ativa, tem prioridade sobre o provedor do ambiente. apiKey ausente mantém a chave salva.")
    @PutMapping("/email")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<EmailIntegrationResponseDTO> updateEmail(@Valid @RequestBody EmailIntegrationRequest request,
            Authentication authentication) {
        EmailIntegrationSettings saved = emailIntegrationUseCase.update(new EmailIntegrationUseCase.UpdateCommand(
                request.isEnabled(), request.getProvider(), request.getFromEmail(), request.getFromName(),
                request.getReplyTo(), request.getApiKey()), authentication.getName());
        Map<String, Object> details = new HashMap<>();
        details.put("integration", "email");
        details.put("enabled", saved.enabled());
        details.put("provider", saved.provider().name());
        details.put("apiKeyChanged", request.getApiKey() != null);
        if (saved.fromEmail() != null) {
            details.put("fromEmail", saved.fromEmail());
        }
        publisher.publishEvent(AuditEvent.of(EventType.INTEGRATION_UPDATED, authentication.getName(), details));
        return ResponseEntity.ok(toResponse(saved));
    }

    @Operation(summary = "Envia e-mails de teste",
            description = "Síncrono: devolve, por e-mail, sucesso ou o erro do provedor. target=STORE (padrão) usa "
                    + "a configuração salva (mesmo desativada); target=ENVIRONMENT usa o provedor do ambiente "
                    + "(Mailpit em dev/hml). Sem 'sample' envia um exemplo de cada um dos e-mails do sistema.")
    @PostMapping("/email/test")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<List<EmailTestResultDTO>> testEmail(@Valid @RequestBody EmailIntegrationTestRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(emailIntegrationUseCase.sendTest(request.getTo(), request.getSample(),
                        request.getTarget(), authentication.getName()).stream()
                .map(r -> new EmailTestResultDTO(r.sample(), r.success(), r.error()))
                .toList());
    }

    // ── WhatsApp (Meta Cloud API) ─────────────────────────────────────────────

    @Operation(summary = "Integração com a WhatsApp Cloud API", description = "Nunca devolve os tokens.")
    @GetMapping("/whatsapp")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<WhatsappIntegrationResponseDTO> getWhatsapp() {
        return ResponseEntity.ok(toResponse(whatsappIntegrationUseCase.get()));
    }

    @Operation(summary = "Atualiza a integração com a WhatsApp Cloud API",
            description = "Tokens ausentes mantêm o valor salvo; \"\" remove. 400 se ativar sem phoneNumberId ou token.")
    @PutMapping("/whatsapp")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<WhatsappIntegrationResponseDTO> updateWhatsapp(
            @Valid @RequestBody WhatsappIntegrationRequest request, Authentication authentication) {
        WhatsappIntegrationSettings saved = whatsappIntegrationUseCase.update(
                new WhatsappIntegrationUseCase.UpdateCommand(request.isEnabled(), request.getPhoneNumberId(),
                        request.getBusinessAccountId(), request.getAccessToken(), request.getVerifyToken()),
                authentication.getName());
        Map<String, Object> details = new HashMap<>();
        details.put("integration", "whatsapp");
        details.put("enabled", saved.enabled());
        details.put("accessTokenChanged", request.getAccessToken() != null);
        details.put("verifyTokenChanged", request.getVerifyToken() != null);
        if (saved.phoneNumberId() != null) {
            details.put("phoneNumberId", saved.phoneNumberId());
        }
        publisher.publishEvent(AuditEvent.of(EventType.INTEGRATION_UPDATED, authentication.getName(), details));
        return ResponseEntity.ok(toResponse(saved));
    }

    @Operation(summary = "Envia um template de teste pelo WhatsApp",
            description = "Usa a configuração salva mesmo desativada. Sem template, envia hello_world (en_US). "
                    + "Recusa da Meta volta 200 com success=false e o erro dela; detail traz o wamid.")
    @PostMapping("/whatsapp/test")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<IntegrationTestResultDTO> testWhatsapp(@Valid @RequestBody WhatsappTestRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(toResponse(whatsappIntegrationUseCase.sendTest(request.getTo(),
                request.getTemplate(), authentication.getName())));
    }

    // ── Plataforma de automação (n8n/Make) ────────────────────────────────────

    @Operation(summary = "Integração com a plataforma de automação (n8n/Make)", description = "Nunca devolve o token.")
    @GetMapping("/automation-platform")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<AutomationPlatformIntegrationResponseDTO> getAutomationPlatform() {
        return ResponseEntity.ok(toResponse(automationPlatformIntegrationUseCase.get()));
    }

    @Operation(summary = "Atualiza a integração com a plataforma de automação",
            description = "token ausente mantém o salvo; \"\" remove. baseUrl precisa ser https:// (http:// só fora de prod).")
    @PutMapping("/automation-platform")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<AutomationPlatformIntegrationResponseDTO> updateAutomationPlatform(
            @Valid @RequestBody AutomationPlatformIntegrationRequest request, Authentication authentication) {
        AutomationPlatformSettings saved = automationPlatformIntegrationUseCase.update(
                new AutomationPlatformIntegrationUseCase.UpdateCommand(request.isEnabled(), request.getPlatform(),
                        request.getBaseUrl(), request.getToken()),
                authentication.getName());
        Map<String, Object> details = new HashMap<>();
        details.put("integration", "automation-platform");
        details.put("enabled", saved.enabled());
        details.put("platform", saved.platform().name());
        details.put("tokenChanged", request.getToken() != null);
        if (saved.baseUrl() != null) {
            details.put("baseUrl", saved.baseUrl());
        }
        publisher.publishEvent(AuditEvent.of(EventType.INTEGRATION_UPDATED, authentication.getName(), details));
        return ResponseEntity.ok(toResponse(saved));
    }

    @Operation(summary = "Testa a plataforma de automação",
            description = "POST {baseUrl} com {\"ping\": true} e o token como Bearer (timeout de 5 s), usando a "
                    + "configuração salva mesmo desativada. detail traz o status HTTP.")
    @PostMapping("/automation-platform/test")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<IntegrationTestResultDTO> testAutomationPlatform(Authentication authentication) {
        return ResponseEntity.ok(toResponse(automationPlatformIntegrationUseCase.sendTest(authentication.getName())));
    }

    private WhatsappIntegrationResponseDTO toResponse(WhatsappIntegrationSettings s) {
        WhatsappIntegrationResponseDTO dto = new WhatsappIntegrationResponseDTO();
        dto.setEnabled(s.enabled());
        dto.setPhoneNumberId(s.phoneNumberId());
        dto.setBusinessAccountId(s.businessAccountId());
        dto.setAccessTokenConfigured(s.accessTokenConfigured());
        dto.setAccessTokenLast4(s.accessTokenLast4());
        dto.setVerifyTokenConfigured(s.verifyTokenConfigured());
        dto.setCallbackUrl(whatsappCallbackUrl);
        WhatsappConnectionStatus status = whatsappIntegrationUseCase.connectionStatus();
        dto.setConnected(status.connected());
        dto.setStatusDetail(status.detail());
        dto.setUpdatedAt(s.updatedAt());
        dto.setUpdatedBy(s.updatedBy());
        return dto;
    }

    private static AutomationPlatformIntegrationResponseDTO toResponse(AutomationPlatformSettings s) {
        AutomationPlatformIntegrationResponseDTO dto = new AutomationPlatformIntegrationResponseDTO();
        dto.setEnabled(s.enabled());
        dto.setPlatform(s.platform());
        dto.setBaseUrl(s.baseUrl());
        dto.setTokenConfigured(s.tokenConfigured());
        dto.setTokenLast4(s.tokenLast4());
        dto.setUpdatedAt(s.updatedAt());
        dto.setUpdatedBy(s.updatedBy());
        return dto;
    }

    private static IntegrationTestResultDTO toResponse(IntegrationTestResult r) {
        return new IntegrationTestResultDTO(r.success(), r.error(), r.detail());
    }

    private EmailIntegrationResponseDTO toResponse(EmailIntegrationSettings s) {
        EmailIntegrationResponseDTO dto = new EmailIntegrationResponseDTO();
        dto.setEnabled(s.enabled());
        dto.setProvider(s.provider());
        dto.setFromEmail(s.fromEmail());
        dto.setFromName(s.fromName());
        dto.setReplyTo(s.replyTo());
        dto.setApiKeyConfigured(s.apiKeyConfigured());
        dto.setApiKeyLast4(s.apiKeyLast4());
        EmailChannelStatus status = emailIntegrationUseCase.activeChannel();
        dto.setActiveProvider(status.provedor());
        dto.setActiveProviderDetail(status.detalhe());
        String environmentProvider = emailIntegrationUseCase.environmentChannel().provedor();
        dto.setEnvironmentProvider(environmentProvider);
        // Só faz sentido quando o ambiente envia para o Mailpit: em prod a aba fica oculta.
        dto.setMailpitUiUrl("MAILPIT".equals(environmentProvider) ? mailpitUiUrl : null);
        dto.setUpdatedAt(s.updatedAt());
        dto.setUpdatedBy(s.updatedBy());
        return dto;
    }
}
