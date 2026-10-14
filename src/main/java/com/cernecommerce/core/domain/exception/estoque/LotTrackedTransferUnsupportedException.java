package com.cernecommerce.core.domain.exception.estoque;

/**
 * Transferência de SKU com controle de lote. A entrada no destino exige lote e validade, e a saída
 * consome por FEFO sem devolver quais lotes saíram: levar os lotes junto é trabalho próprio, que
 * fica para quando houver essência lote-rastreada.
 */
public class LotTrackedTransferUnsupportedException extends RuntimeException {
    public LotTrackedTransferUnsupportedException(String sku) {
        super("Transferência entre depósitos ainda não aceita produto com controle de lote: " + sku);
    }
}
