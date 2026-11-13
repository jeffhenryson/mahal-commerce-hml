package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * "Repor sozinha" — liga/desliga a troca automática da lata de uma essência.
 *
 * <p>{@code limit} sem {@code @Min}/{@code @Max} de propósito: a faixa é conferida no serviço,
 * para sair como {@code AUTO_REPLACE_INVALID_LIMIT}, que é o que o front trata, e não como o erro
 * genérico de validação.</p>
 */
@Data
public class AutoReplaceRequest {

    @NotNull
    @Schema(description = "Ignorado desde 2026-11-13: a lata nunca troca sozinha e grava sempre false",
            example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;

    @Schema(description = "Usos em que o PDV pergunta se a lata acabou, de 1 a 99. Nulo = usar o sessionsPerUnit da lata",
            example = "6", nullable = true)
    private Integer limit;
}
