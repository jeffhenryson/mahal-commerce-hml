package com.cernecommerce.core.domain.model.auth;

/**
 * Resultado de uma etapa do login: tokens, ou o que falta. Só um dos campos vem preenchido, e é por ele
 * que o cliente decide a próxima tela.
 */
public record LoginResponse(
        TokenPair tokenPair,
        String challengeToken,
        boolean twoFactorRequired,
        String enrollmentToken,
        String passwordChangeToken) {

    public static LoginResponse success(TokenPair pair) {
        return new LoginResponse(pair, null, false, null, null);
    }

    public static LoginResponse twoFactorChallenge(String challengeToken) {
        return new LoginResponse(null, challengeToken, true, null, null);
    }

    public static LoginResponse totpEnrollment(String enrollmentToken) {
        return new LoginResponse(null, null, false, enrollmentToken, null);
    }

    public static LoginResponse passwordChange(String passwordChangeToken) {
        return new LoginResponse(null, null, false, null, passwordChangeToken);
    }
}
