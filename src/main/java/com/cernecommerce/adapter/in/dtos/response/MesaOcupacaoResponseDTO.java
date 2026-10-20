package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.MesaOcupacao;

import java.time.Instant;

/** Mesa do QR ocupada, para o atendente: a comanda dela e o código que o cliente pode pedir. */
public record MesaOcupacaoResponseDTO(Long mesaId, Long comandaId, String codigo, Instant abertaEm) {

    public static MesaOcupacaoResponseDTO of(MesaOcupacao o) {
        return new MesaOcupacaoResponseDTO(o.mesaId(), o.comandaId(), o.codigo(), o.abertaEm());
    }
}
