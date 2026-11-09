package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F061 — o pacote da compra de essência já foi pago: trocar o sabor reescreveria uma linha cobrada.
 * Remova e lance de novo, se for o caso.
 * 409 SESSION_PACKAGE_PAID.
 */
public class SessionPackagePaidException extends RuntimeException {

    public SessionPackagePaidException(Long itemId) {
        super("O pacote da sessão " + itemId + " já foi pago: o sabor não pode ser trocado");
    }
}
