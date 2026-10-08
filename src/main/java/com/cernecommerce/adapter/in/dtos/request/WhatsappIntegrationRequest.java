package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Substitui a integração com a WhatsApp Cloud API. Tokens ausentes mantêm o valor salvo; vazios o
 * removem. Nunca são devolvidos.
 */
@Data
public class WhatsappIntegrationRequest {
    @Schema(description = "Ativa o envio por WhatsApp (automações com destino WHATSAPP_META).")
    private boolean enabled;
    @Size(max = 300) @Schema(example = "1234567890", description = "Phone Number ID do app da Meta.")
    private String phoneNumberId;
    @Size(max = 300) @Schema(example = "9876543210", description = "WhatsApp Business Account ID.")
    private String businessAccountId;
    @Size(max = 2000) @Schema(description = "Access token permanente (EAAG...). null = mantém; \"\" = remove.")
    private String accessToken;
    @Size(max = 300) @Schema(description = "Token de verificação do webhook (hub.verify_token). null = mantém; \"\" = remove.")
    private String verifyToken;

    @Override
    public String toString() {
        return "WhatsappIntegrationRequest(enabled=" + enabled + ", phoneNumberId=" + phoneNumberId
                + ", businessAccountId=" + businessAccountId
                + ", accessToken=" + (accessToken == null ? "null" : "***")
                + ", verifyToken=" + (verifyToken == null ? "null" : "***") + ")";
    }
}
