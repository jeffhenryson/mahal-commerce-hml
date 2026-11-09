package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F061 — a sessão já foi recolhida (ou o cliente desistiu): não entra mais adicional nela.
 * 409 SESSION_COLLECTED.
 */
public class SessionCollectedException extends RuntimeException {

    public SessionCollectedException(Long itemId) {
        super("A sessão " + itemId + " já foi recolhida: não aceita mais adicional");
    }
}
