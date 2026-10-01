package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Adicional pago do cardápio de sessão (PDV-F024). */
@Data
public class SessionAddonRequest {

    @NotBlank
    @Size(max = 60)
    @Schema(example = "Filtro de gelo")
    private String nome;

    @NotNull
    @DecimalMin("0.00")
    @Schema(example = "5.00")
    private BigDecimal preco;

    @Min(0)
    private Integer ordem;

    @Schema(description = "Só no PUT; adicional inativo some do cardápio da mesa.", defaultValue = "true")
    private Boolean ativo;
}
