package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.EssenciaDoDia;

import java.time.LocalDate;
import java.util.List;

/** As disponíveis agora, cada uma marcada se está entre as do dia {@code data}. */
public record EssenciaDoDiaResponseDTO(LocalDate data, List<Item> essencias) {

    public record Item(EssenciaOferecidaResponseDTO essencia, boolean selecionada) {
    }

    public static EssenciaDoDiaResponseDTO of(LocalDate data, List<EssenciaDoDia> lista) {
        return new EssenciaDoDiaResponseDTO(data, lista.stream()
                .map(e -> new Item(EssenciaOferecidaResponseDTO.of(e.essencia()), e.selecionada())).toList());
    }
}
