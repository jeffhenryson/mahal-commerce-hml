package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Set;

/** Configuração do cardápio de sessão (PDV-F021). */
@Data
public class SessionSettingsRequest {

    @Size(max = 30)
    @Schema(example = "VASO_P")
    private String vasoPadraoCodigo;

    @Size(max = 30)
    @Schema(example = "VASO_G")
    private String vasoGrandeCodigo;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Schema(example = "10.00")
    private BigDecimal upgradeVasoGrandePreco;

    // PDV-F024 — o rosh duplo virou modo sempre disponível (AddSessionRequest.modo = DUPLO). Os dias
    // continuam valendo só para o POST .../rosh avulso, e saem quando o front deixar de usá-lo.
    @Schema(description = "Dias em que o 2º rosh avulso (POST .../rosh) sai de graça. Deprecado: o rosh "
            + "duplo agora é AddSessionRequest.modo = DUPLO, gratuito em qualquer dia.",
            example = "[\"WEDNESDAY\"]", deprecated = true)
    private Set<DayOfWeek> diasDuploRosh;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "Acréscimo do carvão jumbo na sessão. Omitido = mantém o atual.", example = "2.00")
    private BigDecimal upgradeCarvaoJumboPreco;
}
