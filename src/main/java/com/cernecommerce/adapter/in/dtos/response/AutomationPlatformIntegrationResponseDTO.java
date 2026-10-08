package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.config.AutomationPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/** Integração com n8n/Make sem o token — só se existe e seus 4 últimos caracteres. */
@Data
@Schema(description = "Plataforma de automação (n8n ou Make).")
public class AutomationPlatformIntegrationResponseDTO {
    private boolean enabled;
    private AutomationPlatform platform;
    private String baseUrl;
    private boolean tokenConfigured;
    private String tokenLast4;
    private Instant updatedAt;
    private String updatedBy;
}
