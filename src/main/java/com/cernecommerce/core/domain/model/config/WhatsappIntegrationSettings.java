package com.cernecommerce.core.domain.model.config;

import java.time.Instant;

/**
 * Integração com a WhatsApp Cloud API oficial da Meta como a tela de Integrações enxerga: os
 * tokens nunca saem, só os 4 últimos caracteres do access token e se o verify token existe.
 */
public record WhatsappIntegrationSettings(
        boolean enabled,
        String phoneNumberId,
        String businessAccountId,
        String accessTokenLast4,
        boolean verifyTokenConfigured,
        Instant updatedAt,
        String updatedBy) {

    public boolean accessTokenConfigured() {
        return accessTokenLast4 != null;
    }
}
