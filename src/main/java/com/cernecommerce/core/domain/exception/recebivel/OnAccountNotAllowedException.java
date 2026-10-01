package com.cernecommerce.core.domain.exception.recebivel;

/** Operador sem {@code PDV_SALE_ON_ACCOUNT} tentando marcar. */
public class OnAccountNotAllowedException extends RuntimeException {

    public OnAccountNotAllowedException() {
        super("Marcar exige a permissão PDV_SALE_ON_ACCOUNT");
    }
}
