package com.cernecommerce.core.domain.model.salao;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;

import java.util.List;

/**
 * O que o lead escolheu além do sabor: vaso, carvão, adicionais, quando paga e, na mescla, a
 * proporção dos sabores. O aceite lança a sessão com exatamente isto.
 *
 * @param pagarNoFinal só cliente VIP pode — o resto paga no caixa antes do preparo
 * @param proporcao só com o 2º sabor; o serviço a zera no pedido de um sabor só
 */
public record EscolhasSessao(boolean vasoGrande, Charcoal carvao, List<Long> adicionalIds, boolean pagarNoFinal,
        ProporcaoMistura proporcao) {

    public EscolhasSessao {
        adicionalIds = adicionalIds == null ? List.of() : adicionalIds.stream().distinct().toList();
    }

    public EscolhasSessao(boolean vasoGrande, Charcoal carvao, List<Long> adicionalIds, boolean pagarNoFinal) {
        this(vasoGrande, carvao, adicionalIds, pagarNoFinal, null);
    }

    /** O pedido de antes de V168: vaso padrão, sem carvão nem adicional, pago no final. */
    public static EscolhasSessao padrao() {
        return new EscolhasSessao(false, null, List.of(), true);
    }

    public EscolhasSessao withProporcao(ProporcaoMistura nova) {
        return new EscolhasSessao(vasoGrande, carvao, adicionalIds, pagarNoFinal, nova);
    }
}
