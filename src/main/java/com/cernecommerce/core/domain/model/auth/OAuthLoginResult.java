package com.cernecommerce.core.domain.model.auth;

/**
 * @param login            tokens, ou a pendência (2FA, cadastro de 2FA) que o login ainda exige
 * @param firstGoogleLogin {@code true} quando este login criou a conta ou vinculou o Google a uma
 *                         conta existente — o momento de avisar o dono da conta.
 */
public record OAuthLoginResult(LoginResponse login, String username, boolean firstGoogleLogin) {

    public OAuthLoginResult(TokenPair tokenPair, String username, boolean firstGoogleLogin) {
        this(LoginResponse.success(tokenPair), username, firstGoogleLogin);
    }

    public OAuthLoginResult(TokenPair tokenPair, String username) {
        this(tokenPair, username, false);
    }

    /** Nulo enquanto houver pendência. */
    public TokenPair tokenPair() {
        return login.tokenPair();
    }
}
