package com.cernecommerce.core.domain.model.config;

import java.time.Instant;

/**
 * Integração de e-mail como a tela de Integrações enxerga: tudo menos a chave, da qual só se
 * expõe os 4 últimos caracteres para o operador reconhecer qual está salva.
 *
 * <p>Ativa e com chave, tem prioridade sobre o provedor do ambiente ({@code email.provider}).</p>
 */
public record EmailIntegrationSettings(
        boolean enabled,
        EmailProvider provider,
        String fromEmail,
        String fromName,
        String replyTo,
        String apiKeyLast4,
        Instant updatedAt,
        String updatedBy) {

    public boolean apiKeyConfigured() {
        return apiKeyLast4 != null;
    }
}
