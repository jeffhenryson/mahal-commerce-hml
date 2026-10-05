package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.EmailIntegrationRequest;
import com.cernecommerce.adapter.in.dtos.request.EmailIntegrationTestRequest;
import com.cernecommerce.adapter.in.dtos.response.EmailIntegrationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.EmailTestResultDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.config.EmailIntegrationSettings;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
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
@Tag(name = "Store Integrations", description = "Tokens de integração da loja (e-mail/Resend)")
public class StoreIntegrationController {

    private final EmailIntegrationUseCase emailIntegrationUseCase;
    private final ApplicationEventPublisher publisher;
    private final String mailpitUiUrl;

    public StoreIntegrationController(EmailIntegrationUseCase emailIntegrationUseCase,
            ApplicationEventPublisher publisher, @Value("${mailpit.ui-url:}") String mailpitUiUrl) {
        this.emailIntegrationUseCase = emailIntegrationUseCase;
        this.publisher = publisher;
        this.mailpitUiUrl = mailpitUiUrl == null || mailpitUiUrl.isBlank() ? null : mailpitUiUrl.strip();
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
