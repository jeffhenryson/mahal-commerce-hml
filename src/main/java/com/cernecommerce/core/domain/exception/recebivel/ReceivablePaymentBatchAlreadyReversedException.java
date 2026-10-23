package com.cernecommerce.core.domain.exception.recebivel;

/** V173 — o lote já tem estorno; estornar de novo devolveria o saldo duas vezes. */
public class ReceivablePaymentBatchAlreadyReversedException extends RuntimeException {

    public ReceivablePaymentBatchAlreadyReversedException(Long batchId) {
        super("O recebimento " + batchId + " já foi estornado");
    }
}
