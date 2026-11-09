package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.SessionAddon;

import java.math.BigDecimal;

/** Adicional pago do cardápio de sessão (PDV-F024). */
public record SessionAddonResponseDTO(Long id, String nome, BigDecimal preco, int ordem, boolean ativo,
        boolean permitePosEntrega) {

    public static SessionAddonResponseDTO of(SessionAddon a) {
        return new SessionAddonResponseDTO(a.id(), a.nome(), a.preco(), a.ordem(), a.ativo(), a.permitePosEntrega());
    }
}
