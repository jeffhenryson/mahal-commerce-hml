package com.cernecommerce.adapter.in.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * Substitui a integração da IA. Dois formatos aceitos:
 * <ul>
 *   <li>antigo: {@code {enabled, apiKey?, model?}} — vale para o provedor ativo;</li>
 *   <li>novo: {@code {enabled, provider, fallbackProvider, providers: {OPENAI: {apiKey, model}}}}.</li>
 * </ul>
 * {@code apiKey} ausente mantém a salva; vazia a remove.
 */
@Data
public class AssistantIntegrationRequest {
    private boolean enabled;

    @Schema(description = "Provedor ativo: ANTHROPIC, OPENAI ou GEMINI. Ausente mantém o atual.")
    private String provider;

    @Schema(description = "Provedor reserva (entra se o ativo falhar antes de responder). null remove; ausente mantém.")
    private String fallbackProvider;

    @JsonIgnore
    private boolean fallbackProviderPresent;

    /**
     * Teto mensal de tokens do assistente (entrada + saída, sem cache). Ausente mantém; 0 remove o
     * limite. Acima dele, POST /assistente/.../mensagens responde 409 ASSISTENTE_LIMITE_MENSAL.
     */
    @jakarta.validation.constraints.PositiveOrZero
    private Long monthlyTokenLimit;

    @Valid
    @Schema(description = "Chave e modelo por provedor, ex.: {\"OPENAI\": {\"apiKey\": \"sk-...\", \"model\": \"gpt-5.5\"}}")
    private Map<String, ProviderRequest> providers;

    @Size(max = 2000) @Schema(description = "Formato antigo: chave do provedor ativo. null = mantém; \"\" = remove.")
    private String apiKey;

    @Size(max = 100) @Schema(description = "Formato antigo: modelo do provedor ativo. Ausente mantém o atual.")
    private String model;

    public void setFallbackProvider(String fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
        this.fallbackProviderPresent = true;
    }

    @Data
    public static class ProviderRequest {
        @Size(max = 2000) @Schema(description = "null = mantém; \"\" = remove. Nunca é devolvida.")
        private String apiKey;
        @Size(max = 100)
        private String model;

        @Override
        public String toString() {
            return "ProviderRequest(apiKey=" + (apiKey == null ? "null" : "***") + ", model=" + model + ")";
        }
    }

    @Override
    public String toString() {
        return "AssistantIntegrationRequest(enabled=" + enabled + ", provider=" + provider + ", fallbackProvider="
                + fallbackProvider + ", providers=" + providers + ", apiKey=" + (apiKey == null ? "null" : "***")
                + ", model=" + model + ")";
    }
}
