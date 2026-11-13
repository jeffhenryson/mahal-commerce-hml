package com.cernecommerce.core.domain.exception.pdv;

public class OffSystemReceiptNotFoundException extends RuntimeException {
    public OffSystemReceiptNotFoundException(Long sessionId, Long receiptId) {
        super("Recebimento por fora " + receiptId + " não encontrado na sessão " + sessionId);
    }
}
