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
 * @param perfisConfigurados a casa já marcou os perfis do SKU (mesmo nenhum); falso, {@code perfis} é a
 *        sugestão pelo nome. Só o admin usa: o QR não distingue
 */
public record EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId,
        String tierNome, BigDecimal preco, List<PerfilSabor> perfis, boolean perfisConfigurados) {

    public EssenciaOferecida {
        perfis = perfis == null ? List.of() : perfis.stream().distinct().sorted().toList();
    }

    public EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId, String tierNome,
            BigDecimal preco, List<PerfilSabor> perfis) {
        this(sku, sabor, produto, marca, tierId, tierNome, preco, perfis, false);
    }

    public EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId, String tierNome,
            BigDecimal preco) {
        this(sku, sabor, produto, marca, tierId, tierNome, preco, List.of());
    }
}
