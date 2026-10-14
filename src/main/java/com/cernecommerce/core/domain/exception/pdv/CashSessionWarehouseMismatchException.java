package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F045 — o pedido nomeou um depósito diferente do caixa aberto do operador. O estoque que o
 * balcão mexe é o da gaveta dele, como na venda; outro depósito é movimentação de estoque, com a
 * permissão do estoque. 400.
 */
public class CashSessionWarehouseMismatchException extends RuntimeException {
    public CashSessionWarehouseMismatchException(String requested, String sessionWarehouse) {
        super("O caixa aberto é do depósito " + sessionWarehouse + ", não de " + requested);
    }
}
