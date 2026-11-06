package com.cernecommerce.core.domain.exception.user;

/** Conta de dev alvo de uma ação administrativa sem token elevado (DEV_ELEVATED). */
public class TargetProtectedException extends RuntimeException {
    public TargetProtectedException() {
        super("Esta conta só pode ser alterada com acesso elevado de desenvolvedor");
    }
}
