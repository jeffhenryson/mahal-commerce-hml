package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.EssenciaDoDia;
import com.cernecommerce.core.domain.model.salao.OrigemSelecao;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.SelecaoDoDia;

import java.time.LocalDate;
import java.util.List;

/**
 * As essências do Salão, cada uma marcada se está entre as do dia {@code data}. {@code origem}: a
 * seleção é da própria data (DIA) ou do modelo do dia da semana (MODELO).
 */
public record EssenciaDoDiaResponseDTO(LocalDate data, OrigemSelecao origem, List<Item> essencias) {

    public record Item(EssenciaOferecidaResponseDTO essencia, boolean selecionada) {
    }

    public static EssenciaDoDiaResponseDTO of(LocalDate data, SelecaoDoDia selecao) {
        return new EssenciaDoDiaResponseDTO(data, selecao.origem(), selecao.essencias().stream()
                .map(e -> new Item(EssenciaOferecidaResponseDTO.of(e.essencia()), e.selecionada())).toList());
    }
}
