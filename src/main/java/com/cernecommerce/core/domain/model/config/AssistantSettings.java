package com.cernecommerce.core.domain.model.config;

import java.time.Instant;
import java.util.List;

/**
 * Integração do assistente de IA como a tela de Integrações enxerga. Cada provedor tem a sua chave
 * ({@link #providers}); {@code provider} é o que responde e {@code fallbackProvider} o que entra
 * quando ele falha antes de mostrar qualquer coisa ao usuário.
 */
public record AssistantSettings(
        boolean enabled,
        ProvedorIa provider,
        ProvedorIa fallbackProvider,
        List<AssistantProviderSettings> providers,
        Instant updatedAt,
        String updatedBy) {

    /** Configuração do provedor ativo — os campos "de topo" que o front antigo lê. */
    public AssistantProviderSettings ativo() {
        return doProvedor(provider);
    }

    public AssistantProviderSettings doProvedor(ProvedorIa p) {
        return providers.stream().filter(s -> s.provider() == p).findFirst()
                .orElse(new AssistantProviderSettings(p, null, null, null));
    }

    public boolean apiKeyConfigured() {
        return ativo().apiKeyConfigured();
    }

    public String apiKeyLast4() {
        return ativo().apiKeyLast4();
    }

    public String model() {
        return ativo().model();
    }

    public String statusDetail() {
        return ativo().statusDetail();
    }
}
