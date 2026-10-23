package com.cernecommerce.core.domain.exception.recebivel;

/** V173 — estorno de um recebimento de marcado que não existe. */
public class ReceivablePaymentBatchNotFoundException extends RuntimeException {

    public ReceivablePaymentBatchNotFoundException(Long batchId) {
        super("Recebimento de marcado não encontrado: " + batchId);
    }
}
