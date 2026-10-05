package com.cernecommerce.core.domain.exception.user;

/**
 * PLAT-C054 — conta de cliente da loja ({@code user_type = CUSTOMER}) tem as roles fixadas pelo
 * cadastro da loja. Atribuir role de operador a ela daria acesso ao back-office a quem se
 * cadastrou pela internet; remover {@code ROLE_CUSTOMER} quebraria a conta do cliente.
 */
public class CustomerAccountRolesImmutableException extends RuntimeException {
    public CustomerAccountRolesImmutableException(String username) {
        super("Roles of a shop customer account cannot be changed: " + username);
    }
}
