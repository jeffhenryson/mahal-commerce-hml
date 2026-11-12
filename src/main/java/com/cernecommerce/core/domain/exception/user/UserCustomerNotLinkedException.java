package com.cernecommerce.core.domain.exception.user;

/** O usuário não tem cadastro de cliente vinculado (PLAT-F005) — não há compras para mostrar. */
public class UserCustomerNotLinkedException extends RuntimeException {
    public UserCustomerNotLinkedException(String username) {
        super("Nenhum cadastro de cliente vinculado ao usuário: username=" + username);
    }
}
