package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WhatsappTestRequest {
    @NotBlank @Size(max = 30)
    @Schema(example = "5585999999999", description = "Destino com DDI e DDD, só dígitos.")
    private String to;
    @Size(max = 512)
    @Schema(example = "hello_world", description = "Template aprovado na Meta; ausente = hello_world (en_US).")
    private String template;
}
