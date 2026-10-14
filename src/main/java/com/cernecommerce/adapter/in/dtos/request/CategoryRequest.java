package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Categoria do catálogo, com destaque e ordem de exibição na vitrine")
public class CategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Schema(description = "Categoria em destaque — aparece na primeira linha do app/marketplace. "
            + "Omitido, nasce false.")
    private boolean featured;

    @PositiveOrZero
    @Schema(description = "Posição relativa dentro do grupo (destacadas e não destacadas são "
            + "ordenadas separadamente). Empate desempata por nome.", example = "0")
    private int displayOrder;

    @Schema(description = "PDV-F045 — categoria de cigarros: os produtos saem da grade do PDV e são "
            + "vendidos pela Central de cigarros. Omitido, nasce false.")
    private Boolean cigarette;
}
