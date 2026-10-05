package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.email.InvalidEmailIntegrationException;
import com.cernecommerce.core.domain.model.config.EmailIntegrationSettings;
import com.cernecommerce.core.domain.model.config.EmailProvider;
import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.config.EmailSenderConfig;
import com.cernecommerce.core.domain.model.config.EmailTestResult;
import com.cernecommerce.core.domain.model.config.EmailTestTarget;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.notification.EmailSampleSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Integração de e-mail guardada em {@code system_config} (chaves {@code integration.email.*}, no
 * mesmo esquema do {@link StoreProfileService}). A chave da API é gravada cifrada e nunca volta pela
 * API — só os 4 últimos caracteres, gravados à parte para a leitura não depender de decifrar.
 *
 * <p>As chaves {@code integration.*} ficam fora da listagem de {@code system_config}
 * ({@link SystemConfigService#getAll()}).</p>
 */
public class EmailIntegrationService implements EmailIntegrationUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmailIntegrationService.class);

    public static final String KEY_PREFIX = "integration.";
    static final String ENABLED = "integration.email.enabled";
    static final String PROVIDER = "integration.email.provider";
    static final String FROM_EMAIL = "integration.email.from-email";
    static final String FROM_NAME = "integration.email.from-name";
    static final String REPLY_TO = "integration.email.reply-to";
    static final String API_KEY = "integration.email.resend.api-key";
    static final String API_KEY_LAST4 = "integration.email.resend.api-key-last4";

    private static final int MAX_FIELD = 300;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SystemConfigPort configPort;
    private final SecretCipherPort cipherPort;
    private final EmailSampleSenderPort sampleSender;
    /**
     * Status do {@code EmailPort} em uso. Supplier e não o port: o {@code EmailPort} injetado depende
     * deste serviço para decidir o provedor, e a referência direta fecharia um ciclo de beans.
     */
    private final Supplier<EmailChannelStatus> channelStatus;

    public EmailIntegrationService(SystemConfigPort configPort, SecretCipherPort cipherPort,
            EmailSampleSenderPort sampleSender, Supplier<EmailChannelStatus> channelStatus) {
        this.configPort = configPort;
        this.cipherPort = cipherPort;
        this.sampleSender = sampleSender;
        this.channelStatus = channelStatus;
    }

    @Override
    public EmailChannelStatus activeChannel() {
        return channelStatus.get();
    }

    @Override
    public EmailChannelStatus environmentChannel() {
        return sampleSender.environmentChannel();
    }

    @Override
    public EmailIntegrationSettings get() {
        Optional<SystemConfig> enabled = configPort.findByKey(ENABLED);
        return new EmailIntegrationSettings(
                enabled.map(SystemConfig::asBoolean).orElse(false),
                readProvider(),
                read(FROM_EMAIL), read(FROM_NAME), read(REPLY_TO),
                read(API_KEY) == null ? null : read(API_KEY_LAST4),
                enabled.map(SystemConfig::updatedAt).orElse(null),
                enabled.map(SystemConfig::updatedBy).orElse(null));
    }

    @Override
    @Transactional
    public EmailIntegrationSettings update(UpdateCommand command, String updatedBy) {
        String fromEmail = clean(command.fromEmail(), "Remetente");
        String fromName = clean(command.fromName(), "Nome do remetente");
        String replyTo = clean(command.replyTo(), "Responder para");
        if (fromEmail != null && !EMAIL.matcher(fromEmail).matches()) {
            throw new InvalidEmailIntegrationException("E-mail do remetente inválido: " + fromEmail);
        }
        if (replyTo != null && !EMAIL.matcher(replyTo).matches()) {
            throw new InvalidEmailIntegrationException("E-mail de resposta inválido: " + replyTo);
        }

        String newKey = command.apiKey() == null ? null : command.apiKey().strip();
        boolean keepKey = command.apiKey() == null;
        boolean hasKey = keepKey ? read(API_KEY) != null : !newKey.isEmpty();
        if (command.enabled() && (!hasKey || fromEmail == null)) {
            throw new InvalidEmailIntegrationException(
                    "Para ativar a integração informe a chave da API e o e-mail do remetente");
        }

        Instant now = Instant.now();
        EmailProvider provider = command.provider() == null ? EmailProvider.RESEND : command.provider();
        save(PROVIDER, provider.name(), now, updatedBy);
        save(FROM_EMAIL, fromEmail, now, updatedBy);
        save(FROM_NAME, fromName, now, updatedBy);
        save(REPLY_TO, replyTo, now, updatedBy);
        if (!keepKey) {
            if (newKey.isEmpty()) {
                save(API_KEY, null, now, updatedBy);
                save(API_KEY_LAST4, null, now, updatedBy);
            } else {
                save(API_KEY, cipherPort.encrypt(newKey), now, updatedBy);
                save(API_KEY_LAST4, newKey.substring(Math.max(0, newKey.length() - 4)), now, updatedBy);
            }
        }
        // Por último: é dele que vêm updatedAt/updatedBy da leitura.
        save(ENABLED, String.valueOf(command.enabled()), now, updatedBy);
        return get();
    }

    @Override
    public Optional<EmailSenderConfig> activeSenderConfig() {
        if (!configPort.getBoolean(ENABLED, false)) {
            return Optional.empty();
        }
        return senderConfig();
    }

    @Override
    public List<EmailTestResult> sendTest(String to, EmailSample sample, EmailTestTarget target,
            String requestedBy) {
        String recipient = clean(to, "Destinatário");
        if (recipient == null || !EMAIL.matcher(recipient).matches()) {
            throw new InvalidEmailIntegrationException("Destinatário do teste inválido");
        }
        boolean environment = target == EmailTestTarget.ENVIRONMENT;
        EmailSenderConfig config = environment ? null : senderConfig().orElseThrow(() ->
                new InvalidEmailIntegrationException("Salve a chave da API e o e-mail do remetente antes de testar"));
        List<EmailSample> samples = sample == null ? Arrays.asList(EmailSample.values()) : List.of(sample);
        List<EmailTestResult> results = new ArrayList<>();
        for (EmailSample s : samples) {
            try {
                if (environment) {
                    sampleSender.sendEnvironmentSample(recipient, s);
                } else {
                    sampleSender.sendSample(config, recipient, s);
                }
                results.add(EmailTestResult.ok(s));
            } catch (Exception ex) {
                results.add(EmailTestResult.failed(s, ex.getMessage()));
            }
        }
        log.info("email.integration.test to={} target={} samples={} failures={} by={}", recipient,
                environment ? EmailTestTarget.ENVIRONMENT : EmailTestTarget.STORE, samples.size(),
                results.stream().filter(r -> !r.success()).count(), requestedBy);
        return results;
    }

    private Optional<EmailSenderConfig> senderConfig() {
        String encrypted = read(API_KEY);
        String fromEmail = read(FROM_EMAIL);
        if (encrypted == null || fromEmail == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new EmailSenderConfig(readProvider(), cipherPort.decrypt(encrypted), fromEmail,
                    read(FROM_NAME), read(REPLY_TO)));
        } catch (Exception ex) {
            // Chave de cifra trocada: melhor cair no provedor do ambiente do que parar todo e-mail.
            log.error("email.integration.decrypt.failed error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    private EmailProvider readProvider() {
        String value = read(PROVIDER);
        try {
            return value == null ? EmailProvider.RESEND : EmailProvider.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return EmailProvider.RESEND;
        }
    }

    private String read(String key) {
        return configPort.findByKey(key).map(SystemConfig::value).filter(v -> !v.isBlank()).orElse(null);
    }

    private void save(String key, String value, Instant now, String updatedBy) {
        configPort.save(new SystemConfig(key, value == null ? "" : value, now, updatedBy));
    }

    private static String clean(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > MAX_FIELD) {
            throw new InvalidEmailIntegrationException(field + " excede " + MAX_FIELD + " caracteres");
        }
        return v;
    }
}
