package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.config.EmailProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Substitui a integração de e-mail. {@code apiKey} ausente mantém a chave salva; vazia a remove. */
@Data
public class EmailIntegrationRequest {
    @Schema(description = "Ativa o envio pelo provedor da loja (tem prioridade sobre o provedor do ambiente).")
    private boolean enabled;
    @Schema(description = "Provedor. Só RESEND por enquanto; ausente = RESEND.")
    private EmailProvider provider;
    @Size(max = 300) @Schema(example = "contato@mahaltabacaria.com.br",
            description = "Remetente. O domínio precisa estar verificado no Resend.")
    private String fromEmail;
    @Size(max = 300) @Schema(example = "Mahal Tabacaria")
    private String fromName;
    @Size(max = 300) private String replyTo;
    @Size(max = 300) @Schema(description = "Chave da API (re_...). null = mantém a atual; \"\" = remove. Nunca é devolvida.")
    private String apiKey;

    @Override
    public String toString() {
        return "EmailIntegrationRequest(enabled=" + enabled + ", provider=" + provider + ", fromEmail=" + fromEmail
                + ", apiKey=" + (apiKey == null ? "null" : "***") + ")";
    }
}
