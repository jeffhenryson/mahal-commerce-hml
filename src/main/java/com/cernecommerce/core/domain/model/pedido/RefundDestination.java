package com.cernecommerce.core.domain.model.pedido;

/**
 * Para onde vai a mercadoria de um item estornado (PED-F004). O dinheiro volta nos três casos.
 */
public enum RefundDestination {
    /** Voltou inteira e entra de novo no estoque vendável. */
    RESTOCK,
    /**
     * Voltou, mas não dá para vender: perda/avaria. O saldo não muda — a unidade já saiu do estoque
     * na venda, e uma SAIDA nova baixaria duas vezes. A perda fica registrada na linha do estorno.
     */
    DISCARD,
    /** Só o dinheiro volta; a mercadoria fica com o cliente e o estoque não muda. */
    CUSTOMER_KEEPS;

    /** Só RESTOCK mexe no saldo. */
    public boolean returnsToStock() {
        return this == RESTOCK;
    }
}
