package com.cernecommerce.core.domain.model.auth;

/** Quem definiu a senha, e se foi aceitando um convite em vez de recuperando a conta. */
public record PasswordResetResult(String username, boolean invite) {
}
