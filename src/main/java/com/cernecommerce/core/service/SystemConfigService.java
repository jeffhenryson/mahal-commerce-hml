package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SystemConfigService implements SystemConfigUseCase {

    private static final Set<String> PUBLIC_KEYS = Set.of(
        "auth.google.enabled",
        "auth.forgot-password.enabled"
    );

    private final SystemConfigPort configPort;

    public SystemConfigService(SystemConfigPort configPort) {
        this.configPort = configPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getAllPublic() {
        return configPort.findAll().stream()
            .filter(c -> PUBLIC_KEYS.contains(c.key()))
            .collect(Collectors.toMap(SystemConfig::key, SystemConfig::value));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getAll() {
        // integration.* guarda tokens de integração (cifrados) — têm tela e permissão próprias.
        return configPort.findAll().stream()
            .filter(c -> !c.key().startsWith(EmailIntegrationService.KEY_PREFIX))
            .collect(Collectors.toMap(SystemConfig::key, SystemConfig::value));
    }

    @Override
    @Transactional
    public void set(String key, String value, String updatedBy) {
        if (!PUBLIC_KEYS.contains(key)) {
            throw new IllegalArgumentException("Chave de configuração inválida: " + key);
        }
        configPort.save(new SystemConfig(key, value, Instant.now(), updatedBy));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return configPort.getBoolean(key, defaultValue);
    }

    @Override
    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return configPort.getInt(key, defaultValue);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        return configPort.getDecimal(key, defaultValue);
    }
}
