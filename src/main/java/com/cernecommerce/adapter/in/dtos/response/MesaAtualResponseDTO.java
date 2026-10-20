package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.MesaAtual;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A mesa ocupada como o lead com acesso a vê. */
public record MesaAtualResponseDTO(String mesa, String cliente, String codigo, Instant abertaEm, List<Item> itens,
        BigDecimal emAberto) {

    public record Item(String nome, BigDecimal quantidade, BigDecimal valor, boolean pago, Instant lancadoEm) {
    }

    public static MesaAtualResponseDTO of(MesaAtual m) {
        return new MesaAtualResponseDTO(m.mesa(), m.cliente(), m.codigo(), m.abertaEm(),
                m.itens().stream().map(i -> new Item(i.nome(), i.quantidade(), i.valor(), i.pago(), i.lancadoEm()))
                        .toList(),
                m.emAberto());
    }
}
