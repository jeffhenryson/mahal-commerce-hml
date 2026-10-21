package com.cernecommerce.core.domain.model.salao;

import java.math.BigDecimal;
import java.util.List;

/**
 * Essência que o QR oferece ao lead: lata aberta no depósito das sessões e com faixa ativa. Só o necessário para escolher — nada de custo ou saldo.
 *
 * @param sabor o sabor (atributos da variação), ou o nome do produto quando não há variação
 * @param produto nome do produto (a linha da marca)
 * @param perfis os marcados pela casa para o SKU, ou os sugeridos pelo nome enquanto ela não marcou;
 *        na ordem de {@link PerfilSabor}
 */
public record EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId,
        String tierNome, BigDecimal preco, List<PerfilSabor> perfis) {

    public EssenciaOferecida {
        perfis = perfis == null ? List.of() : perfis.stream().distinct().sorted().toList();
    }

    public EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId, String tierNome,
            BigDecimal preco) {
        this(sku, sabor, produto, marca, tierId, tierNome, preco, List.of());
    }
}
