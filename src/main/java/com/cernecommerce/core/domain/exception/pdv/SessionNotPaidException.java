package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-C026 — rosh na fila mandado ao preparo sem estar pago: a sessão raiz ainda aguarda pagamento,
 * ou a própria linha é cobrada e ninguém a cobrou. Desde PDV-F027 nada vai ao preparo antes de pago;
 * sem esta guarda o {@code NA_FILA → PREPARANDO} contornava a regra. 409.
 */
public class SessionNotPaidException extends RuntimeException {
    public SessionNotPaidException(Long itemId) {
        super("A linha " + itemId + " não pode ir ao preparo antes de paga");
    }
}
