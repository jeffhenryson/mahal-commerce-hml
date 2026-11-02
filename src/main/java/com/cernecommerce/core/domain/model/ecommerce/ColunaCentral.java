package com.cernecommerce.core.domain.model.ecommerce;

import java.util.EnumSet;
import java.util.Set;

/**
 * As colunas do quadro da Central de Pedidos › App (pendência 10 §1). Cada uma é um conjunto de
 * {@link AtendimentoStatus}; FINALIZADOS ainda exige o começo do dia local ({@code from}).
 */
public enum ColunaCentral {
    NOVOS(EnumSet.of(AtendimentoStatus.PENDENTE)),
    PREPARO(EnumSet.of(AtendimentoStatus.ACEITO)),
    SAIU(EnumSet.of(AtendimentoStatus.PRONTO, AtendimentoStatus.SAIU_PARA_ENTREGA)),
    FINALIZADOS(EnumSet.of(AtendimentoStatus.RETIRADO, AtendimentoStatus.ENTREGUE));

    /** Tamanhos de página do seletor da coluna. */
    public static final Set<Integer> TAMANHOS = Set.of(5, 10, 20, 50, 100);

    private final Set<AtendimentoStatus> statuses;

    ColunaCentral(Set<AtendimentoStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<AtendimentoStatus> statuses() {
        return statuses;
    }
}
