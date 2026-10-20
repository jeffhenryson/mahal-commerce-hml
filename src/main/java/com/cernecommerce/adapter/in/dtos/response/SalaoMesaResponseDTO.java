package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.SalaoMesa;

import java.time.Instant;

public record SalaoMesaResponseDTO(Long id, String nome, String token, boolean ativo, Instant criadoEm) {

    public static SalaoMesaResponseDTO of(SalaoMesa m) {
        return new SalaoMesaResponseDTO(m.id(), m.nome(), m.token(), m.ativo(), m.criadoEm());
    }
}
