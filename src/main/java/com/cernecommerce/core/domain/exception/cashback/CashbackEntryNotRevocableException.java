package com.cernecommerce.core.domain.exception.cashback;

/**
 * Só um ganho ({@code EARNED}) que ainda não foi revertido nem expirado pode ser revogado: revogar um
 * resgate, um estorno ou um ganho já anulado não tem sentido contábil.
 */
public class CashbackEntryNotRevocableException extends RuntimeException {
    public CashbackEntryNotRevocableException(Long entryId, String reason) {
        super("Lançamento de cashback " + entryId + " não pode ser revogado: " + reason);
    }
}
