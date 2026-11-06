package com.cernecommerce.core.domain.exception.auth;

/** Token de pendência de login inexistente, vencido, já usado ou de outra finalidade. */
public class PendingLoginTokenInvalidException extends RuntimeException {
    public PendingLoginTokenInvalidException() {
        super("Link de acesso inválido ou expirado. Entre novamente.");
    }
}
