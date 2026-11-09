package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F061 — a essência não está marcada para a modalidade escolhida (as deluxe ficam de fora por não
 * serem marcadas).
 * 409 SESSION_ESSENCE_NOT_ALLOWED.
 */
public class SessionEssenceNotAllowedException extends RuntimeException {

    public SessionEssenceNotAllowedException(String sku, String modalidade) {
        super("A essência " + sku + " não está liberada para a modalidade " + modalidade);
    }
}
