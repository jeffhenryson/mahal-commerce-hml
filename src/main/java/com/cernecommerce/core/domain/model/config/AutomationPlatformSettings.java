package com.cernecommerce.core.domain.model.config;

import java.time.Instant;

/** Integração com n8n/Make como a tela de Integrações enxerga: o token só pelos 4 últimos caracteres. */
public record AutomationPlatformSettings(
        boolean enabled,
        AutomationPlatform platform,
        String baseUrl,
        String tokenLast4,
        Instant updatedAt,
        String updatedBy) {

    public boolean tokenConfigured() {
        return tokenLast4 != null;
    }
}
