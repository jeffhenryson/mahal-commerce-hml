package com.cernecommerce.core.domain.model.crm;

import java.util.List;

/** Blocos que vão no payload do disparo (destinos WEBHOOK e PLATAFORMA). */
public enum AutomationMetadata {
    CLIENTE,
    LOJA,
    /** Só tem conteúdo em eventos de pedido/venda. */
    PEDIDO,
    DATA,
    AUTOMACAO;

    /** Padrão quando a automação não escolhe: tudo menos {@link #PEDIDO}. */
    public static final List<AutomationMetadata> DEFAULT = List.of(CLIENTE, LOJA, DATA, AUTOMACAO);
}
