package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.config.AutomationPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Substitui a integração com n8n/Make. {@code token} ausente mantém o salvo; vazio o remove. */
@Data
public class AutomationPlatformIntegrationRequest {
    private boolean enabled;
    @Schema(description = "N8N ou MAKE; ausente = N8N.")
    private AutomationPlatform platform;
    @Size(max = 300) @Schema(example = "https://n8n.seudominio.com/webhook",
            description = "Base dos webhooks; https:// (http:// só fora de prod).")
    private String baseUrl;
    @Size(max = 2000) @Schema(description = "Token enviado como Bearer. null = mantém; \"\" = remove. Nunca é devolvido.")
    private String token;

    @Override
    public String toString() {
        return "AutomationPlatformIntegrationRequest(enabled=" + enabled + ", platform=" + platform
                + ", baseUrl=" + baseUrl + ", token=" + (token == null ? "null" : "***") + ")";
    }
}
