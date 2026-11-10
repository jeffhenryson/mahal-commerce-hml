package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.AssistantIntegrationRequest;
import com.cernecommerce.adapter.in.dtos.request.AutomationPlatformIntegrationRequest;
import com.cernecommerce.adapter.in.dtos.request.EmailIntegrationRequest;
import com.cernecommerce.adapter.in.dtos.request.EmailIntegrationTestRequest;
import com.cernecommerce.adapter.in.dtos.request.WhatsappIntegrationRequest;
import com.cernecommerce.adapter.in.dtos.request.WhatsappTestRequest;
import com.cernecommerce.adapter.in.dtos.response.AssistantIntegrationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistantProviderOptionDTO;
import com.cernecommerce.adapter.in.dtos.response.AutomationPlatformIntegrationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.EmailIntegrationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.EmailTestResultDTO;
import com.cernecommerce.adapter.in.dtos.response.IntegrationHistoryEntryDTO;
import com.cernecommerce.adapter.in.dtos.response.IntegrationTestResultDTO;
import com.cernecommerce.adapter.in.dtos.response.WhatsappIntegrationResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.config.AssistantSettings;
import com.cernecommerce.core.domain.model.config.ProvedorIa;
import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.domain.model.config.AutomationPlatformSettings;
import com.cernecommerce.core.domain.model.config.EmailIntegrationSettings;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.WhatsappConnectionStatus;
import com.cernecommerce.core.domain.model.config.WhatsappIntegrationSettings;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
import com.cernecommerce.core.ports.in.IntegrationHistoryUseCase;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/store/integrations")
@Tag(name = "Store Integrations", description = "Tokens de integração da loja (e-mail/Resend, WhatsApp, n8n/Make, IA do assistente)")
public class StoreIntegrationController {

    private final EmailIntegrationUseCase emailIntegrationUseCase;
    private final WhatsappIntegrationUseCase whatsappIntegrationUseCase;
    private final AutomationPlatformIntegrationUseCase automationPlatformIntegrationUseCase;
    private final AssistantIntegrationUseCase assistantIntegrationUseCase;
    private final IntegrationHistoryUseCase integrationHistoryUseCase;
    private final ApplicationEventPublisher publisher;
    private final String mailpitUiUrl;
    private final String whatsappCallbackUrl;

    public StoreIntegrationController(EmailIntegrationUseCase emailIntegrationUseCase,
            WhatsappIntegrationUseCase whatsappIntegrationUseCase,
            AutomationPlatformIntegrationUseCase automationPlatformIntegrationUseCase,
            AssistantIntegrationUseCase assistantIntegrationUseCase,
            IntegrationHistoryUseCase integrationHistoryUseCase,
            ApplicationEventPublisher publisher, @Value("${mailpit.ui-url:}") String mailpitUiUrl,
            @Value("${whatsapp.webhook-url:}") String whatsappCallbackUrl) {
        this.emailIntegrationUseCase = emailIntegrationUseCase;
        this.whatsappIntegrationUseCase = whatsappIntegrationUseCase;
        this.automationPlatformIntegrationUseCase = automationPlatformIntegrationUseCase;
        this.assistantIntegrationUseCase = assistantIntegrationUseCase;
        this.integrationHistoryUseCase = integrationHistoryUseCase;
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

    // ── IA do assistente (Claude, ChatGPT, Gemini) ────────────────────────────

    @Operation(summary = "Integração da IA do assistente", description = "Nunca devolve as chaves.")
    @GetMapping("/assistant")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<AssistantIntegrationResponseDTO> getAssistant() {
        return ResponseEntity.ok(toResponse(assistantIntegrationUseCase.get()));
    }

    @Operation(summary = "Provedores de IA suportados pelo assistente", description = "Com o modelo padrão de cada um.")
    @GetMapping("/assistant/providers")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<List<AssistantProviderOptionDTO>> assistantProviders() {
        Map<ProvedorIa, String> modelos = assistantIntegrationUseCase.defaultModels();
        return ResponseEntity.ok(java.util.Arrays.stream(ProvedorIa.values())
                .map(p -> new AssistantProviderOptionDTO(p.name(), p.rotulo(), modelos.get(p)))
                .toList());
    }

    @Operation(summary = "Atualiza a integração da IA do assistente",
            description = "Uma chave por provedor (ANTHROPIC, OPENAI, GEMINI), um ativo e um reserva opcional. "
                    + "apiKey ausente mantém a chave salva; \"\" remove. Aceita também o formato antigo "
                    + "{enabled, apiKey, model}, aplicado ao provedor ativo. 400 se ativar sem a chave do ativo, "
                    + "reserva sem chave ou igual ao ativo, ou modelo que não é do provedor.")
    @PutMapping("/assistant")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<AssistantIntegrationResponseDTO> updateAssistant(
            @Valid @RequestBody AssistantIntegrationRequest request, Authentication authentication) {
        AssistantSettings before = assistantIntegrationUseCase.get();
        ProvedorIa provider = provedor(request.getProvider());
        Map<ProvedorIa, AssistantIntegrationUseCase.ProviderCommand> comandos = new java.util.EnumMap<>(ProvedorIa.class);
        if (request.getProviders() != null) {
            request.getProviders().forEach((nome, c) -> comandos.put(provedorObrigatorio(nome),
                    new AssistantIntegrationUseCase.ProviderCommand(c == null ? null : c.getApiKey(),
                            c == null ? null : c.getModel())));
        }
        AssistantSettings saved = assistantIntegrationUseCase.update(new AssistantIntegrationUseCase.UpdateCommand(
                request.isEnabled(), provider, provedor(request.getFallbackProvider()),
                request.isFallbackProviderPresent(), comandos, request.getApiKey(), request.getModel()),
                authentication.getName());

        // Formato antigo: a chave e o modelo do topo foram para o provedor ativo.
        if ((request.getApiKey() != null || request.getModel() != null) && !comandos.containsKey(saved.provider())) {
            comandos.put(saved.provider(), new AssistantIntegrationUseCase.ProviderCommand(request.getApiKey(),
                    request.getModel()));
        }
        List<String> fields = new ArrayList<>();
        boolean secretSet = false;
        boolean secretRemoved = false;
        if (before.enabled() != saved.enabled()) {
            fields.add("enabled");
        }
        if (before.provider() != saved.provider()) {
            fields.add("provider");
        }
        if (before.fallbackProvider() != saved.fallbackProvider()) {
            fields.add("fallbackProvider");
        }
        for (Map.Entry<ProvedorIa, AssistantIntegrationUseCase.ProviderCommand> e : comandos.entrySet()) {
            String apiKey = e.getValue().apiKey();
            if (apiKey != null) {
                fields.add("providers." + e.getKey().name() + ".apiKey");
                secretRemoved |= apiKey.isBlank();
                secretSet |= !apiKey.isBlank();
            }
            if (!java.util.Objects.equals(before.doProvedor(e.getKey()).model(), saved.doProvedor(e.getKey()).model())) {
                fields.add("providers." + e.getKey().name() + ".model");
            }
        }
        String historyAction = secretRemoved ? "SECRET_REMOVED"
                : secretSet ? "SECRET_SET"
                : before.enabled() != saved.enabled() ? (saved.enabled() ? "ENABLED" : "DISABLED")
                : "UPDATED";
        Map<String, Object> details = new HashMap<>();
        details.put("integration", "assistant");
        details.put("enabled", saved.enabled());
        details.put("provider", saved.provider().name());
        if (saved.fallbackProvider() != null) {
            details.put("fallbackProvider", saved.fallbackProvider().name());
        }
        details.put("apiKeyChanged", secretSet || secretRemoved);
        details.put("historyAction", historyAction);
        details.put("fields", fields);
        publisher.publishEvent(AuditEvent.of(EventType.INTEGRATION_UPDATED, authentication.getName(), details));
        return ResponseEntity.ok(toResponse(saved));
    }

    @Operation(summary = "Testa a IA do assistente",
            description = "Chamada mínima ao provedor com a chave salva dele (mesmo desativado). Sem provider, testa "
                    + "o ativo. Recusa do provedor volta 200 com success=false e o motivo em error; detail traz o modelo.")
    @PostMapping("/assistant/test")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<IntegrationTestResultDTO> testAssistant(@RequestParam(required = false) String provider,
            Authentication authentication) {
        return ResponseEntity.ok(toResponse(assistantIntegrationUseCase.sendTest(provedor(provider),
                authentication.getName())));
    }

    @Operation(summary = "Histórico de alterações da IA do assistente",
            description = "Mais recente primeiro. Nunca traz valores, só os campos alterados.")
    @GetMapping("/assistant/history")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public ResponseEntity<List<IntegrationHistoryEntryDTO>> assistantHistory(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(integrationHistoryUseCase.history("assistant", limit).stream()
                .map(e -> new IntegrationHistoryEntryDTO(e.at(), e.user(), e.action(), e.fields()))
                .toList());
    }

    private static ProvedorIa provedorObrigatorio(String valor) {
        ProvedorIa p = provedor(valor);
        if (p == null) {
            throw new InvalidIntegrationException("Informe o provedor de IA (ANTHROPIC, OPENAI ou GEMINI)");
        }
        return p;
    }

    private static ProvedorIa provedor(String valor) {
        try {
            return ProvedorIa.de(valor);
        } catch (IllegalArgumentException ex) {
            throw new InvalidIntegrationException("Provedor de IA desconhecido: " + valor
                    + " (use ANTHROPIC, OPENAI ou GEMINI)");
        }
    }

    private static AssistantIntegrationResponseDTO toResponse(AssistantSettings s) {
        AssistantIntegrationResponseDTO dto = new AssistantIntegrationResponseDTO();
        dto.setEnabled(s.enabled());
        dto.setProvider(s.provider().name());
        dto.setFallbackProvider(s.fallbackProvider() == null ? null : s.fallbackProvider().name());
        dto.setApiKeyConfigured(s.apiKeyConfigured());
        dto.setApiKeyLast4(s.apiKeyLast4());
        dto.setModel(s.model());
        dto.setSource("STORE");
        dto.setStatusDetail(s.statusDetail());
        dto.setProviders(s.providers().stream().map(p -> {
            AssistantIntegrationResponseDTO.Provider item = new AssistantIntegrationResponseDTO.Provider();
            item.setProvider(p.provider().name());
            item.setLabel(p.provider().rotulo());
            item.setApiKeyConfigured(p.apiKeyConfigured());
            item.setApiKeyLast4(p.apiKeyLast4());
            item.setModel(p.model());
            item.setStatusDetail(p.statusDetail());
            return item;
        }).toList());
        dto.setUpdatedAt(s.updatedAt());
        dto.setUpdatedBy(s.updatedBy());
        return dto;
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
