package com.cernecommerce.core.domain.model.auth;

/** Por que o token existe: quem o usa define a senha nos dois casos, mas o efeito é outro. */
public enum PasswordResetPurpose {
    RESET,
    INVITE
}
