package com.cernecommerce.core.domain.exception.cashback;

/** Lançamento de cashback inexistente, ou de outro cliente. */
public class CashbackEntryNotFoundException extends RuntimeException {
    public CashbackEntryNotFoundException(Long entryId) {
        super("Lançamento de cashback " + entryId + " não encontrado para este cliente");
    }
}
