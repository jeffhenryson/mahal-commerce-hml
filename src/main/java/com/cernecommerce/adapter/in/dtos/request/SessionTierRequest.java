package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Faixa do cardápio de sessão (PDV-F021). */
@Data
public class SessionTierRequest {

    @NotBlank
    @Size(max = 60)
    @Schema(example = "Premium")
    private String nome;

    @NotNull
    @DecimalMin("0.00")
    @Schema(example = "30.00")
    private BigDecimal preco;

    @Size(max = 255)
    @Schema(description = "Marcas de essência da faixa, texto livre para a tela.", example = "Luk, Smynar, Nay")
    private String marcas;

    @Min(0)
    private Integer ordem;

    @Schema(description = "Só no PUT; faixa inativa some do cardápio da mesa.", defaultValue = "true")
    private Boolean ativo;
}
