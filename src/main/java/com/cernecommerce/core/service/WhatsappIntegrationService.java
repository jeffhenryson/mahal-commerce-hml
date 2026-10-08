package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.integration.IntegrationConfigStore;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.config.WhatsappConnectionStatus;
import com.cernecommerce.core.domain.model.config.WhatsappCredentials;
import com.cernecommerce.core.domain.model.config.WhatsappIntegrationSettings;
import com.cernecommerce.core.domain.model.config.WhatsappSendResult;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.notification.WhatsappPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * WhatsApp Cloud API da Meta em {@code system_config}, chaves {@code integration.whatsapp.*}. O
 * access token e o verify token são gravados cifrados e nunca voltam pela API.
 *
 * <p>{@link #connectionStatus()} consulta a Graph API e guarda o resultado por alguns minutos: ele
 * alimenta a tela de Integrações e o badge de canais do CRM, que é lido a cada abertura de tela.</p>
 */
public class WhatsappIntegrationService implements WhatsappIntegrationUseCase {

    private static final Logger log = LoggerFactory.getLogger(WhatsappIntegrationService.class);

    static final String ENABLED = "integration.whatsapp.enabled";
    static final String PHONE_NUMBER_ID = "integration.whatsapp.phone-number-id";
    static final String BUSINESS_ACCOUNT_ID = "integration.whatsapp.business-account-id";
    static final String ACCESS_TOKEN = "integration.whatsapp.access-token";
    static final String ACCESS_TOKEN_LAST4 = "integration.whatsapp.access-token-last4";
    static final String VERIFY_TOKEN = "integration.whatsapp.verify-token";

    static final String DEFAULT_TEST_TEMPLATE = "hello_world";
    static final String DEFAULT_TEST_LANGUAGE = "en_US";
    static final String CUSTOM_TEST_LANGUAGE = "pt_BR";

    private static final Pattern META_ID = Pattern.compile("^\\d{5,30}$");
    private static final Pattern TEMPLATE_NAME = Pattern.compile("^[a-z0-9_]{1,512}$");

    private final IntegrationConfigStore store;
    private final WhatsappPort whatsappPort;
    private final Clock clock;
    private final Duration statusTtl;

    // Cache do status: um par imutável trocado de uma vez (lido sem lock — no pior caso, duas
    // checagens simultâneas na Graph API).
    private volatile WhatsappConnectionStatus cachedStatus;
    private volatile Instant cachedUntil;

    public WhatsappIntegrationService(SystemConfigPort configPort, SecretCipherPort cipherPort,
            WhatsappPort whatsappPort, Clock clock, Duration statusTtl) {
        this.store = new IntegrationConfigStore(configPort, cipherPort);
        this.whatsappPort = whatsappPort;
        this.clock = clock;
        this.statusTtl = statusTtl;
    }

    @Override
    public WhatsappIntegrationSettings get() {
        Optional<SystemConfig> enabled = store.find(ENABLED);
        return new WhatsappIntegrationSettings(
                enabled.map(SystemConfig::asBoolean).orElse(false),
                store.read(PHONE_NUMBER_ID),
                store.read(BUSINESS_ACCOUNT_ID),
                store.read(ACCESS_TOKEN) == null ? null : store.read(ACCESS_TOKEN_LAST4),
                store.read(VERIFY_TOKEN) != null,
                enabled.map(SystemConfig::updatedAt).orElse(null),
                enabled.map(SystemConfig::updatedBy).orElse(null));
    }

    @Override
    @Transactional
    public WhatsappIntegrationSettings update(UpdateCommand command, String updatedBy) {
        String phoneNumberId = metaId(command.phoneNumberId(), "Phone Number ID");
        String businessAccountId = metaId(command.businessAccountId(), "WhatsApp Business Account ID");
        if (command.enabled() && (phoneNumberId == null || !store.willHaveSecret(command.accessToken(), ACCESS_TOKEN))) {
            throw new InvalidIntegrationException(
                    "Para ativar a integração informe o Phone Number ID e o access token");
        }

        Instant now = Instant.now();
        store.save(PHONE_NUMBER_ID, phoneNumberId, now, updatedBy);
        store.save(BUSINESS_ACCOUNT_ID, businessAccountId, now, updatedBy);
        store.applySecret(command.accessToken(), ACCESS_TOKEN, ACCESS_TOKEN_LAST4, now, updatedBy);
        store.applySecret(command.verifyToken(), VERIFY_TOKEN, null, now, updatedBy);
        // Por último: é dele que vêm updatedAt/updatedBy da leitura.
        store.save(ENABLED, String.valueOf(command.enabled()), now, updatedBy);
        cachedStatus = null;
        cachedUntil = null;
        return get();
    }

    @Override
    public WhatsappConnectionStatus connectionStatus() {
        if (!store.readBoolean(ENABLED)) {
            return WhatsappConnectionStatus.disconnected("Integração desativada");
        }
        Optional<WhatsappCredentials> credentials = savedCredentials();
        if (credentials.isEmpty()) {
            return WhatsappConnectionStatus.disconnected("Integração incompleta: falta o Phone Number ID ou o access token");
        }
        WhatsappConnectionStatus cached = cachedStatus;
        Instant until = cachedUntil;
        Instant now = clock.instant();
        if (cached != null && until != null && until.isAfter(now)) {
            return cached;
        }
        WhatsappCredentials c = credentials.get();
        WhatsappSendResult check = whatsappPort.checkPhoneNumber(c.phoneNumberId(), c.accessToken());
        WhatsappConnectionStatus status = check.success()
                ? WhatsappConnectionStatus.up()
                : WhatsappConnectionStatus.disconnected(check.error());
        cachedUntil = now.plus(statusTtl);
        cachedStatus = status;
        return status;
    }

    @Override
    public IntegrationTestResult sendTest(String to, String template, String requestedBy) {
        String recipient = to == null ? "" : to.replaceAll("\\D", "");
        if (recipient.length() < 10 || recipient.length() > 15) {
            throw new InvalidIntegrationException("Número de destino inválido: use DDI + DDD + número, ex.: 5585999999999");
        }
        String name = template == null || template.isBlank() ? null : template.strip();
        if (name != null && !TEMPLATE_NAME.matcher(name).matches()) {
            throw new InvalidIntegrationException("Nome de template inválido: " + name);
        }
        WhatsappCredentials credentials = savedCredentials().orElseThrow(() ->
                new InvalidIntegrationException("Salve o Phone Number ID e o access token antes de testar"));

        WhatsappSendResult result = whatsappPort.sendTemplate(credentials.phoneNumberId(), credentials.accessToken(),
                recipient,
                name == null ? DEFAULT_TEST_TEMPLATE : name,
                name == null ? DEFAULT_TEST_LANGUAGE : CUSTOM_TEST_LANGUAGE,
                List.of());
        log.info("whatsapp.integration.test success={} by={}", result.success(), requestedBy);
        return result.success()
                ? IntegrationTestResult.ok(result.messageId())
                : IntegrationTestResult.failed(result.error(), null);
    }

    @Override
    public Optional<WhatsappCredentials> activeCredentials() {
        if (!store.readBoolean(ENABLED)) {
            return Optional.empty();
        }
        return savedCredentials();
    }

    @Override
    public boolean matchesVerifyToken(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        return store.decrypt(VERIFY_TOKEN)
                .map(saved -> MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8),
                        candidate.getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    private Optional<WhatsappCredentials> savedCredentials() {
        String phoneNumberId = store.read(PHONE_NUMBER_ID);
        if (phoneNumberId == null) {
            return Optional.empty();
        }
        return store.decrypt(ACCESS_TOKEN).map(token -> new WhatsappCredentials(phoneNumberId, token));
    }

    private static String metaId(String value, String field) {
        String v = IntegrationConfigStore.clean(value, field);
        if (v != null && !META_ID.matcher(v).matches()) {
            throw new InvalidIntegrationException(field + " deve conter apenas dígitos");
        }
        return v;
    }

}
