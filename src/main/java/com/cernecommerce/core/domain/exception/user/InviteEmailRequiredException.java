package com.cernecommerce.core.domain.exception.user;

/** Convite (usuário criado sem senha) exige e-mail: é por ele que a pessoa define a senha. */
public class InviteEmailRequiredException extends RuntimeException {
    public InviteEmailRequiredException() {
        super("Informe o e-mail para enviar o convite — ou defina uma senha para o usuário.");
    }
}
