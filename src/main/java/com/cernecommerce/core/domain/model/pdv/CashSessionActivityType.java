package com.cernecommerce.core.domain.model.pdv;

/**
 * O que o operador fez no turno além de vender (PDV-F047). {@link #OUTRO} exige observação: sem ela
 * a linha não diz nada.
 */
public enum CashSessionActivityType {
    VARRER_LOJA,
    PASSAR_PANO,
    TROCAR_DINHEIRO,
    REPOSICAO,
    LIMPEZA_BANHEIRO,
    OUTRO
}
