package com.cernecommerce.core.domain.model.estoque;

/**
 * Modo de um produto {@link ProductType#KIT}.
 *
 * <p>{@link #LOJA}: kit montado na loja a partir de uma receita de produtos do catálogo (EST-F015) —
 * sem saldo próprio, o saldo é derivado dos componentes. Padrão de todo kit anterior a este campo.</p>
 *
 * <p>{@link #PRE_MONTADO}: kit que chega montado; o estoque é do kit inteiro, como um SIMPLES, e os
 * itens ({@link KitItem}) só descrevem o conteúdo. Imutável depois da criação.</p>
 */
public enum KitMode {
    LOJA,
    PRE_MONTADO
}
