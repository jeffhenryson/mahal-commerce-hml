package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.EssenciaOferecida;
import com.cernecommerce.core.domain.model.salao.PerfilSabor;

import java.math.BigDecimal;
import java.util.List;

public record EssenciaOferecidaResponseDTO(String sku, String sabor, String produto, String marca, Long tierId,
        String tierNome, BigDecimal preco, List<PerfilSabor> perfis, boolean perfisConfigurados) {

    public static EssenciaOferecidaResponseDTO of(EssenciaOferecida e) {
        return new EssenciaOferecidaResponseDTO(e.sku(), e.sabor(), e.produto(), e.marca(), e.tierId(), e.tierNome(),
                e.preco(), e.perfis(), e.perfisConfigurados());
    }
}
