package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Integração da IA do assistente sem as chaves — só se existem e seus 4 últimos caracteres.
 * Os campos de topo ({@code apiKeyConfigured}, {@code apiKeyLast4}, {@code model},
 * {@code statusDetail}) são do provedor ativo, para o card antigo continuar funcionando.
 */
@Data
@Schema(description = "Chaves de IA (Claude, ChatGPT, Gemini) usadas pelo assistente do admin.")
public class AssistantIntegrationResponseDTO {
    private boolean enabled;
    @Schema(description = "Provedor ativo: ANTHROPIC, OPENAI ou GEMINI.")
    private String provider;
    @Schema(description = "Provedor reserva, ou null.")
    private String fallbackProvider;
    private boolean apiKeyConfigured;
    private String apiKeyLast4;
    @Schema(description = "Modelo do provedor ativo.")
    private String model;
    @Schema(description = "Sempre STORE: o assistente só usa as chaves cadastradas na loja.")
    private String source;
    @Schema(description = "Resultado do último teste do provedor ativo.")
    private String statusDetail;
    private List<Provider> providers;
    private Instant updatedAt;
    private String updatedBy;
    /** Teto mensal de tokens (entrada + saída); null = sem limite. */
    private Long monthlyTokenLimit;

    @Data
    @Schema(name = "AssistantIntegrationProvider")
    public static class Provider {
        private String provider;
        private String label;
        private boolean apiKeyConfigured;
        private String apiKeyLast4;
        private String model;
        private String statusDetail;
    }
}
