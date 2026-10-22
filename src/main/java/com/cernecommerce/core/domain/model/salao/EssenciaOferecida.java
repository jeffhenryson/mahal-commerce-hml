package com.cernecommerce.core.domain.model.salao;

import java.math.BigDecimal;
import java.util.List;

/**
 * Essência do Salão: produto de sessão com lata aberta ou fechada no depósito das sessões. O QR
 * oferece as que têm faixa ativa ({@link Disponibilidade#noQr}); o admin vê todas. Só o necessário
 * para escolher — nada de custo ou saldo.
 *
 * @param sabor o sabor (atributos da variação), ou o nome do produto quando não há variação
 * @param produto nome do produto (a linha da marca)
 * @param tierId nulo em {@link Disponibilidade#SEM_FAIXA}, como {@code tierNome} e {@code preco}
 * @param perfis os marcados pela casa para o SKU, ou os sugeridos pelo nome enquanto ela não marcou;
 *        na ordem de {@link PerfilSabor}
 * @param perfisConfigurados a casa já marcou os perfis do SKU (mesmo nenhum); falso, {@code perfis} é a
 *        sugestão pelo nome. Só o admin usa: o QR não distingue
 * @param motivoFora por que não está com lata aberta, para o admin; nulo em {@link Disponibilidade#ABERTA}
 */
public record EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId,
        String tierNome, BigDecimal preco, List<PerfilSabor> perfis, boolean perfisConfigurados,
        Disponibilidade disponibilidade, String motivoFora) {

    public EssenciaOferecida {
        perfis = perfis == null ? List.of() : perfis.stream().distinct().sorted().toList();
        disponibilidade = disponibilidade == null ? Disponibilidade.ABERTA : disponibilidade;
    }

    public EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId, String tierNome,
            BigDecimal preco, List<PerfilSabor> perfis, boolean perfisConfigurados) {
        this(sku, sabor, produto, marca, tierId, tierNome, preco, perfis, perfisConfigurados,
                Disponibilidade.ABERTA, null);
    }

    public EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId, String tierNome,
            BigDecimal preco, List<PerfilSabor> perfis) {
        this(sku, sabor, produto, marca, tierId, tierNome, preco, perfis, false);
    }

    public EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId, String tierNome,
            BigDecimal preco) {
        this(sku, sabor, produto, marca, tierId, tierNome, preco, List.of());
    }

    public EssenciaOferecida comPerfis(List<PerfilSabor> perfis, boolean perfisConfigurados) {
        return new EssenciaOferecida(sku, sabor, produto, marca, tierId, tierNome, preco, perfis, perfisConfigurados,
                disponibilidade, motivoFora);
    }
}
