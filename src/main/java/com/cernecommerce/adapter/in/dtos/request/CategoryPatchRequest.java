package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Alteração parcial de categoria. Campo ausente ou nulo significa <b>não mexer</b>, e por isso
 * {@code featured}/{@code displayOrder} são wrappers — sem valor "vazio" natural, um
 * {@code boolean} primitivo faria corpo vazio virar "tirar do destaque" silenciosamente.
 *
 * <p>{@code active} não está aqui: tem endpoint próprio, pelo mesmo motivo de produto e depósito
 * (EST-F018) — desativar merece evento de auditoria distinto de uma correção de nome.</p>
 */
@Data
public class CategoryPatchRequest {

    @Size(min = 1, max = 100)
    private String name;

    private Boolean featured;

    @PositiveOrZero
    private Integer displayOrder;

    /** PDV-F045 — categoria de cigarros. Omitido não muda. */
    private Boolean cigarette;

    /** V197 — categoria fora dos benefícios (sem cashback, sem resgate, sem desconto do chefe). Omitido não muda. */
    private Boolean cashbackExcluded;

    /**
     * V205 — cor no PDV. Omitido/nulo não muda; {@code ""} remove a cor; {@code "#RRGGBB"} troca.
     * A string vazia é a exceção à regra "nulo não mexe": sem ela não haveria como tirar a cor.
     */
    @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "Use uma cor #RRGGBB ou vazio para remover")
    @Schema(description = "V205 — cor no PDV. Omitido ou nulo não muda; \"\" remove; \"#RRGGBB\" troca.",
            example = "#F5B638", nullable = true)
    private String color;
}
