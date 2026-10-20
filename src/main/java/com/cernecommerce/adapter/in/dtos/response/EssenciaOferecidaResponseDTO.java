package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.EssenciaOferecida;

import java.math.BigDecimal;

public record EssenciaOferecidaResponseDTO(String sku, String sabor, String produto, String marca, Long tierId,
        String tierNome, BigDecimal preco) {

    public static EssenciaOferecidaResponseDTO of(EssenciaOferecida e) {
        return new EssenciaOferecidaResponseDTO(e.sku(), e.sabor(), e.produto(), e.marca(), e.tierId(), e.tierNome(),
                e.preco());
    }
}
