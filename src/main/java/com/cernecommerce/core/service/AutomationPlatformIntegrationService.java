package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.integration.IntegrationConfigStore;
import com.cernecommerce.core.domain.model.config.AutomationPlatform;
import com.cernecommerce.core.domain.model.config.AutomationPlatformSettings;
import com.cernecommerce.core.domain.model.config.AutomationPlatformTarget;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.crm.CampaignWebhookPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Plataforma de automação (n8n ou Make) em {@code system_config}, chaves
 * {@code integration.automation-platform.*}. Automações com destino PLATAFORMA postam em
 * {@code {baseUrl}/{workflowPath}} com o token como Bearer.
 */
public class AutomationPlatformIntegrationService implements AutomationPlatformIntegrationUseCase {

    private static final Logger log = LoggerFactory.getLogger(AutomationPlatformIntegrationService.class);

    static final String ENABLED = "integration.automation-platform.enabled";
    static final String PLATFORM = "integration.automation-platform.platform";
    static final String BASE_URL = "integration.automation-platform.base-url";
    static final String TOKEN = "integration.automation-platform.token";
    static final String TOKEN_LAST4 = "integration.automation-platform.token-last4";

    private final IntegrationConfigStore store;
    private final CampaignWebhookPort webhookPort;
    /** {@code http://} só fora de prod — em prod o token iria em texto puro pela rede. */
    private final boolean allowInsecureHttp;

    public AutomationPlatformIntegrationService(SystemConfigPort configPort, SecretCipherPort cipherPort,
            CampaignWebhookPort webhookPort, boolean allowInsecureHttp) {
        this.store = new IntegrationConfigStore(configPort, cipherPort);
        this.webhookPort = webhookPort;
        this.allowInsecureHttp = allowInsecureHttp;
    }

    @Override
    public AutomationPlatformSettings get() {
        Optional<SystemConfig> enabled = store.find(ENABLED);
        return new AutomationPlatformSettings(
                enabled.map(SystemConfig::asBoolean).orElse(false),
                readPlatform(),
                store.read(BASE_URL),
                store.read(TOKEN) == null ? null : store.read(TOKEN_LAST4),
                enabled.map(SystemConfig::updatedAt).orElse(null),
                enabled.map(SystemConfig::updatedBy).orElse(null));
    }

    @Override
    @Transactional
    public AutomationPlatformSettings update(UpdateCommand command, String updatedBy) {
        String baseUrl = IntegrationConfigStore.clean(command.baseUrl(), "URL base");
        if (baseUrl != null) {
            validateBaseUrl(baseUrl);
        }
        if (command.enabled() && baseUrl == null) {
            throw new InvalidIntegrationException("Para ativar a integração informe a URL base");
        }

        Instant now = Instant.now();
        AutomationPlatform platform = command.platform() == null ? AutomationPlatform.N8N : command.platform();
        store.save(PLATFORM, platform.name(), now, updatedBy);
        store.save(BASE_URL, baseUrl, now, updatedBy);
        store.applySecret(command.token(), TOKEN, TOKEN_LAST4, now, updatedBy);
        // Por último: é dele que vêm updatedAt/updatedBy da leitura.
        store.save(ENABLED, String.valueOf(command.enabled()), now, updatedBy);
        return get();
    }

    @Override
    public IntegrationTestResult sendTest(String requestedBy) {
        AutomationPlatformTarget target = savedTarget().orElseThrow(() ->
                new InvalidIntegrationException("Salve a URL base antes de testar"));
        WebhookDispatchResult result = webhookPort.send(target.baseUrl(), authHeaders(target), Map.of("ping", true));
        log.info("automation-platform.integration.test success={} status={} by={}", result.success(),
                result.statusCode(), requestedBy);
        String detail = result.statusCode() == null ? null : "HTTP " + result.statusCode();
        return result.success()
                ? IntegrationTestResult.ok(detail)
                : IntegrationTestResult.failed(result.errorMessage(), detail);
    }

    @Override
    public Optional<AutomationPlatformTarget> activeTarget() {
        if (!store.readBoolean(ENABLED)) {
            return Optional.empty();
        }
        return savedTarget();
    }

    /** Cabeçalho de autenticação do destino — vazio quando a plataforma não tem token. */
    public static Map<String, String> authHeaders(AutomationPlatformTarget target) {
        return target.token() == null ? Map.of() : Map.of("Authorization", "Bearer " + target.token());
    }

    private Optional<AutomationPlatformTarget> savedTarget() {
        String baseUrl = store.read(BASE_URL);
        if (baseUrl == null) {
            return Optional.empty();
        }
        boolean hasToken = store.read(TOKEN) != null;
        Optional<String> token = store.decrypt(TOKEN);
        if (hasToken && token.isEmpty()) {
            // Token salvo mas indecifrável: melhor não postar sem autenticação.
            return Optional.empty();
        }
        return Optional.of(new AutomationPlatformTarget(baseUrl, token.orElse(null)));
    }

    private void validateBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw new InvalidIntegrationException("URL base inválida: " + baseUrl);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        boolean secure = scheme.equals("https");
        boolean insecureAllowed = scheme.equals("http") && allowInsecureHttp;
        if (!(secure || insecureAllowed) || uri.getHost() == null) {
            throw new InvalidIntegrationException("A URL base precisa começar com https://");
        }
    }

    private AutomationPlatform readPlatform() {
        String value = store.read(PLATFORM);
        try {
            return value == null ? AutomationPlatform.N8N : AutomationPlatform.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return AutomationPlatform.N8N;
        }
    }
}
