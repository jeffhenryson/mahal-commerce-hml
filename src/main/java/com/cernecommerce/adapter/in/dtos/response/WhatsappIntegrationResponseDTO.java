package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/** Integração com a WhatsApp Cloud API sem os tokens — só se existem e os 4 últimos do access token. */
@Data
@Schema(description = "Integração com a WhatsApp Cloud API oficial da Meta.")
public class WhatsappIntegrationResponseDTO {
    private boolean enabled;
    private String phoneNumberId;
    private String businessAccountId;
    private boolean accessTokenConfigured;
    private String accessTokenLast4;
    private boolean verifyTokenConfigured;
    @Schema(description = "URL pública deste backend que a Meta chama — cadastrar no app da Meta.")
    private String callbackUrl;
    @Schema(description = "Se o token salvo alcança o número na Graph API (cache de alguns minutos).")
    private boolean connected;
    @Schema(description = "Motivo quando connected = false.")
    private String statusDetail;
    private Instant updatedAt;
    private String updatedBy;
}
