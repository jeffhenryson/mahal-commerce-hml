package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.salao.PerfilSabor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Perfis de sabor por SKU. Substitui os de cada SKU listado; lista vazia limpa. */
public record EssenciasPerfisRequest(@NotNull @Size(max = 1000) List<@Valid @NotNull Item> itens) {

    public record Item(@NotBlank @Size(max = 50) String sku,
            @NotNull @Size(max = 6) List<@NotNull PerfilSabor> perfis) {
    }
}
