package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.AcessoMesa;
import com.cernecommerce.core.domain.model.salao.CardapioDaMesa;
import com.cernecommerce.core.domain.model.salao.PerfilSabor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Página pública do QR: só o necessário para escolher — sem custo, saldo ou ids internos de produto.
 * {@code acesso} diz se este aparelho pode pedir (LIVRE/LIBERADO) ou precisa do código (BLOQUEADO).
 */
public record CardapioDaMesaResponseDTO(String mesa, List<Faixa> faixas, List<Essencia> essencias,
        BigDecimal upgradeVasoGrande, BigDecimal upgradeCarvaoJumbo, List<Adicional> adicionais, AcessoMesa acesso) {

    public record Faixa(Long id, String nome, BigDecimal preco, String marcas) {
    }

    public record Essencia(String sku, String sabor, String produto, String marca, Long tierId, String tierNome,
            BigDecimal preco, boolean destaque, List<PerfilSabor> perfis) {
    }

    public record Adicional(Long id, String nome, BigDecimal preco) {
    }

    public static CardapioDaMesaResponseDTO of(CardapioDaMesa c) {
        return new CardapioDaMesaResponseDTO(c.mesa(),
                c.faixas().stream().map(f -> new Faixa(f.id(), f.nome(), f.preco(), f.marcas())).toList(),
                c.essencias().stream().map(e -> new Essencia(e.sku(), e.sabor(), e.produto(), e.marca(), e.tierId(),
                        e.tierNome(), e.preco(), c.destaques().contains(e.sku()), e.perfis())).toList(),
                c.upgradeVasoGrande(), c.upgradeCarvaoJumbo(),
                c.adicionais().stream().map(a -> new Adicional(a.id(), a.nome(), a.preco())).toList(),
                c.acesso());
    }
}
