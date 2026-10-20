package com.cernecommerce.core.domain.model.salao;

import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionTier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * O que a página pública do QR mostra: a mesa, as faixas ativas, as essências oferecidas (com os
 * destaques do dia), os extras com preço e se este aparelho pode pedir na mesa.
 */
public record CardapioDaMesa(String mesa, List<SessionTier> faixas, List<EssenciaOferecida> essencias,
        Set<String> destaques, BigDecimal upgradeVasoGrande, BigDecimal upgradeCarvaoJumbo,
        List<SessionAddon> adicionais, AcessoMesa acesso) {

    public CardapioDaMesa {
        faixas = List.copyOf(faixas);
        essencias = List.copyOf(essencias);
        destaques = Set.copyOf(destaques);
        adicionais = List.copyOf(adicionais);
    }
}
