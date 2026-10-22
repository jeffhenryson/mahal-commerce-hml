package com.cernecommerce.adapter.in.dtos.response;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** O modelo semanal das essências do dia: sempre os 7 dias, 1 = segunda. */
public record EssenciaModeloResponseDTO(List<Dia> dias) {

    public record Dia(int diaSemana, List<String> skus) {
    }

    public static EssenciaModeloResponseDTO of(Map<Integer, Set<String>> modelo) {
        return new EssenciaModeloResponseDTO(java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(d -> new Dia(d, List.copyOf(modelo.getOrDefault(d, Set.of())))).toList());
    }
}
