package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.config.EmailProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/** Integração de e-mail sem a chave — só se ela existe e seus 4 últimos caracteres. */
@Data
@Schema(description = "Integração de e-mail da loja (Resend).")
public class EmailIntegrationResponseDTO {
    private boolean enabled;
    private EmailProvider provider;
    private String fromEmail;
    private String fromName;
    private String replyTo;
    private boolean apiKeyConfigured;
    private String apiKeyLast4;
    @Schema(description = "Provedor que está enviando agora (RESEND, MAILPIT, LOG).")
    private String activeProvider;
    @Schema(description = "Descrição do provedor ativo.")
    private String activeProviderDetail;
    @Schema(description = "Provedor do ambiente, usado quando a integração está desativada (RESEND, MAILPIT, LOG).")
    private String environmentProvider;
    @Schema(description = "URL da caixa de entrada do Mailpit para o navegador; null fora de dev/hml.")
    private String mailpitUiUrl;
    private Instant updatedAt;
    private String updatedBy;
}
