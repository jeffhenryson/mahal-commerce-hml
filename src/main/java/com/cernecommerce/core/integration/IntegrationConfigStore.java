package com.cernecommerce.core.integration;

import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/**
 * Leitura/gravação das chaves {@code integration.*} de {@code system_config} com a regra comum de
 * segredos das integrações: cifrado em repouso ({@link SecretCipherPort}), os 4 últimos caracteres
 * gravados à parte, {@code null} mantém o valor salvo e {@code ""} o remove. Mesmo esquema do
 * {@code EmailIntegrationService}.
 *
 * <p>Fora de {@code core.service} de propósito: lá toda classe é um caso de uso (implementa uma
 * port — {@code HexagonalArchitectureTest}); este é só o apoio comum a eles.</p>
 */
public final class IntegrationConfigStore {

    private static final Logger log = LoggerFactory.getLogger(IntegrationConfigStore.class);
    public static final int MAX_FIELD = 300;

    private final SystemConfigPort configPort;
    private final SecretCipherPort cipherPort;

    public IntegrationConfigStore(SystemConfigPort configPort, SecretCipherPort cipherPort) {
        this.configPort = configPort;
        this.cipherPort = cipherPort;
    }

    public Optional<SystemConfig> find(String key) {
        return configPort.findByKey(key);
    }

    public String read(String key) {
        return configPort.findByKey(key).map(SystemConfig::value).filter(v -> !v.isBlank()).orElse(null);
    }

    public boolean readBoolean(String key) {
        return configPort.getBoolean(key, false);
    }

    public void save(String key, String value, Instant now, String updatedBy) {
        configPort.save(new SystemConfig(key, value == null ? "" : value, now, updatedBy));
    }

    /** Se o segredo existirá depois de aplicar {@code raw} (mantido, trocado ou removido). */
    public boolean willHaveSecret(String raw, String key) {
        return raw == null ? read(key) != null : !raw.isBlank();
    }

    /**
     * Aplica a regra de segredo: {@code null} não mexe, em branco remove, senão cifra. Devolve se
     * houve mudança — vai para o audit log como {@code xxxChanged}, nunca o valor.
     */
    public boolean applySecret(String raw, String key, String last4Key, Instant now, String updatedBy) {
        if (raw == null) {
            return false;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            save(key, null, now, updatedBy);
            if (last4Key != null) {
                save(last4Key, null, now, updatedBy);
            }
        } else {
            if (value.length() > 2000) {
                throw new InvalidIntegrationException("Segredo excede 2000 caracteres");
            }
            save(key, cipherPort.encrypt(value), now, updatedBy);
            if (last4Key != null) {
                save(last4Key, last4(value), now, updatedBy);
            }
        }
        return true;
    }

    /** Segredo decifrado, ou vazio quando ausente ou indecifrável (chave de cifra trocada). */
    public Optional<String> decrypt(String key) {
        String encrypted = read(key);
        if (encrypted == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(cipherPort.decrypt(encrypted));
        } catch (Exception ex) {
            log.error("integration.decrypt.failed key={} error={}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    public static String last4(String value) {
        return value.substring(Math.max(0, value.length() - 4));
    }

    public static String clean(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > MAX_FIELD) {
            throw new InvalidIntegrationException(field + " excede " + MAX_FIELD + " caracteres");
        }
        return v;
    }
}
