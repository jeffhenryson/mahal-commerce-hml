package com.cernecommerce.core.domain.model.salao;

import java.math.BigDecimal;

/**
 * Essência que o QR oferece ao lead: lata aberta no depósito das sessões e com faixa ativa. Só o necessário para escolher — nada de custo ou saldo.
 *
 * @param sabor o sabor (atributos da variação), ou o nome do produto quando não há variação
 * @param produto nome do produto (a linha da marca)
 */
public record EssenciaOferecida(String sku, String sabor, String produto, String marca, Long tierId,
        String tierNome, BigDecimal preco) {
}
