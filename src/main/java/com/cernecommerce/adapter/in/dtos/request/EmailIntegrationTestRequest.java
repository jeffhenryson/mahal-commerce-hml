package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.config.EmailTestTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailIntegrationTestRequest {
    @NotBlank @Email
    private String to;
    @Schema(description = "Qual e-mail enviar; ausente envia um exemplo de cada.")
    private EmailSample sample;
    @Schema(description = "STORE (padrão): Resend salvo na loja. ENVIRONMENT: provedor do ambiente "
            + "(Mailpit em dev/hml), não exige a integração configurada.")
    private EmailTestTarget target;
}
