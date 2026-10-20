package com.cernecommerce.core.domain.model.salao;

import com.cernecommerce.core.domain.model.pdv.Charcoal;

import java.util.List;

/**
 * O que o lead escolheu além do sabor: vaso, carvão, adicionais e quando paga. O aceite lança a
 * sessão com exatamente isto.
 *
 * @param pagarNoFinal só cliente VIP pode — o resto paga no caixa antes do preparo
 */
public record EscolhasSessao(boolean vasoGrande, Charcoal carvao, List<Long> adicionalIds, boolean pagarNoFinal) {

    public EscolhasSessao {
        adicionalIds = adicionalIds == null ? List.of() : adicionalIds.stream().distinct().toList();
    }

    /** O pedido de antes de V168: vaso padrão, sem carvão nem adicional, pago no final. */
    public static EscolhasSessao padrao() {
        return new EscolhasSessao(false, null, List.of(), true);
    }
}
