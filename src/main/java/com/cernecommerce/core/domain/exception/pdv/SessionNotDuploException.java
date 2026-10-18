package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F055 — desfazer o duplo de uma sessão que não tem rosh extra ligado. 409: a sessão existe, o
 * estado dela é que não tem o que desfazer.
 */
public class SessionNotDuploException extends RuntimeException {
    public SessionNotDuploException(Long itemId) {
        super("Esta sessão não é rosh duplo (linha " + itemId + ").");
    }
}
