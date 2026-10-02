package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F034 — sessão "paga no final" lançada por quem não tem {@code PDV_SESSION_PAY_LATER}.
 *
 * <p>403 pela mesma razão da cortesia: mandar o narguilé ao salão sem receber é aceitar o risco de a
 * mesa sair sem pagar, e esse risco tem dono.</p>
 */
public class SessionPayLaterNotAllowedException extends RuntimeException {
    public SessionPayLaterNotAllowedException(String username) {
        super("Usuário " + username + " não tem permissão para lançar sessão paga no final");
    }
}
