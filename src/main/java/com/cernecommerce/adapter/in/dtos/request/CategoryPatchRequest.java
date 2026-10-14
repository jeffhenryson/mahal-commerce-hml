package com.cernecommerce.adapter.in.dtos.request;

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
}
