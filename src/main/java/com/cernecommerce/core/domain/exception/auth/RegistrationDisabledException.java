package com.cernecommerce.core.domain.exception.auth;

/** Auto-cadastro desativado: só dev e admin criam usuários do painel. */
public class RegistrationDisabledException extends RuntimeException {
    public RegistrationDisabledException() {
        super("Cadastro desativado. Peça a um administrador para criar seu usuário.");
    }
}
