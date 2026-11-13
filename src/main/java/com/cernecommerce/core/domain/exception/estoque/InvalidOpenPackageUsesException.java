package com.cernecommerce.core.domain.exception.estoque;

/**
 * Sessões restantes fora do que a lata rende (EST-F033). O teto é o {@code sessionsPerUnit} do
 * catálogo, que só o service conhece — por isso não é Bean Validation no DTO.
 */
public class InvalidOpenPackageUsesException extends RuntimeException {
    public InvalidOpenPackageUsesException(int usesRemaining, int sessionsPerUnit) {
        super("Sessões restantes devem estar entre 1 e " + sessionsPerUnit + ", recebido " + usesRemaining);
    }

    private InvalidOpenPackageUsesException(String message) {
        super(message);
    }

    /** Correção manual dos usos da lata aberta fora de 0..max. */
    public static InvalidOpenPackageUsesException adjusted(int uses, int max) {
        return new InvalidOpenPackageUsesException("Usos da lata devem estar entre 0 e " + max + ", recebido " + uses);
    }
}
