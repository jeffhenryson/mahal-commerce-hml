package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.ports.in.EstoqueUseCase;

/** PDV-F061 — uma essência do cardápio de sessão e as modalidades em que ela vale. */
public record SessionEssenceResponseDTO(String sku, String nome, String marca, Long faixaId, boolean compraEssencia,
        boolean itensPessoais) {

    public static SessionEssenceResponseDTO of(EstoqueUseCase.SessionEssence e) {
        return new SessionEssenceResponseDTO(e.sku(), e.nome(), e.marca(), e.faixaId(), e.compraEssencia(),
                e.itensPessoais());
    }
}
