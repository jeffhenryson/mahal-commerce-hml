package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** SKUs das essências do dia. Lista vazia = o QR oferece todas as disponíveis. */
public record EssenciasDoDiaRequest(@NotNull @Size(max = 1000) List<@Size(max = 50) String> skus) {
}
