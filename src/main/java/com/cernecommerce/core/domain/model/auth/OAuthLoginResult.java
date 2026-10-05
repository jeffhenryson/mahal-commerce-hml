package com.cernecommerce.core.domain.model.auth;

/**
 * @param firstGoogleLogin {@code true} quando este login criou a conta ou vinculou o Google a uma
 *                         conta existente — o momento de avisar o dono da conta.
 */
public record OAuthLoginResult(TokenPair tokenPair, String username, boolean firstGoogleLogin) {

    public OAuthLoginResult(TokenPair tokenPair, String username) {
        this(tokenPair, username, false);
    }
}
