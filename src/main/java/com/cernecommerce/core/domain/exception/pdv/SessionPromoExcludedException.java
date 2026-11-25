package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F062 — a essência está marcada "fora da promo" (as deluxe).
 * 409 SESSION_PROMO_EXCLUDED.
 */
public class SessionPromoExcludedException extends RuntimeException {

    private final String sku;

    public SessionPromoExcludedException(String sku) {
        super("A essência " + sku + " está fora da promo de essência");
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
