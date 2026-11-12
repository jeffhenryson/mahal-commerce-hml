package com.cernecommerce.core.domain.model.config;

/** Um provedor de IA cadastrado: a chave só pelos 4 últimos caracteres. */
public record AssistantProviderSettings(
        ProvedorIa provider,
        String apiKeyLast4,
        String model,
        String statusDetail) {

    public boolean apiKeyConfigured() {
        return apiKeyLast4 != null;
    }
}
