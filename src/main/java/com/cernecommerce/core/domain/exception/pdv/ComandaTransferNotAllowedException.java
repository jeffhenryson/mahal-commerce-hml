package com.cernecommerce.core.domain.exception.pdv;

/**
 * Transferência de mesas entre caixas recusada (PDV-F054): mesmo caixa nos dois lados, mesa que não é
 * do caixa de origem, ou depósito do caixa de destino diferente do da mesa. O último importa: o
 * estoque de cada linha saiu do depósito da comanda, e é o caixa que recebe a mesa que passa a
 * responder por ela. Vira 409 {@code COMANDA_TRANSFER_NOT_ALLOWED}.
 */
public class ComandaTransferNotAllowedException extends RuntimeException {

    public ComandaTransferNotAllowedException(String reason) {
        super(reason);
    }
}
