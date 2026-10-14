package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F053 — converter em duplo uma sessão que já tem rosh extra ligado. 409: a sessão existe, o
 * estado dela é que impede (um segundo 2º rosh cortesia seria narguilé de graça).
 */
public class SessionAlreadyDuploException extends RuntimeException {
    public SessionAlreadyDuploException(Long itemId) {
        super("Esta sessão já é rosh duplo (linha " + itemId + ").");
    }
}
