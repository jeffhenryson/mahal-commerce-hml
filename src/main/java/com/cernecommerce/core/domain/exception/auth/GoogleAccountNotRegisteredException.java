package com.cernecommerce.core.domain.exception.auth;

/** Login Google com um e-mail que não pertence a nenhum usuário — o login não cria contas. */
public class GoogleAccountNotRegisteredException extends RuntimeException {
    public GoogleAccountNotRegisteredException() {
        super("Nenhum usuário cadastrado com este e-mail. Peça acesso a um administrador.");
    }
}
