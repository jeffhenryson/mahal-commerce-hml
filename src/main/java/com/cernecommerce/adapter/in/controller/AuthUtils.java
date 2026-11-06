package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.response.PendingLoginResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TwoFactorChallengeResponseDTO;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.PendingLoginToken;
import org.springframework.security.core.Authentication;

final class AuthUtils {

    private static final long CHALLENGE_TTL_SECONDS = 300;

    private AuthUtils() {}

    /** Corpo da resposta quando o login ainda tem pendência; {@code null} quando vieram os tokens. */
    static Object pendingLoginBody(LoginResponse login) {
        if (login.twoFactorRequired()) {
            return new TwoFactorChallengeResponseDTO("PENDING_2FA", login.challengeToken(), CHALLENGE_TTL_SECONDS);
        }
        if (login.passwordChangeToken() != null) {
            return new PendingLoginResponseDTO("PASSWORD_CHANGE_REQUIRED", null, login.passwordChangeToken(),
                    PendingLoginToken.TTL_SECONDS);
        }
        if (login.enrollmentToken() != null) {
            return new PendingLoginResponseDTO("TOTP_ENROLLMENT_REQUIRED", login.enrollmentToken(), null,
                    PendingLoginToken.TTL_SECONDS);
        }
        return null;
    }

    /**
     * Retorna true se o caller tem ROLE_DEV ou DEV_ELEVATED.
     * Null-safe: retorna false se {@code auth} for null (ex: contexto de teste sem segurança).
     */
    static boolean isDevAccess(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DEV".equals(a.getAuthority())
                        || "DEV_ELEVATED".equals(a.getAuthority()));
    }

    /**
     * Retorna true se o caller tem {@code authority}. Para quando {@code @PreAuthorize} libera a rota
     * por mais de uma permissão e o comportamento muda conforme qual delas ele tem (PDV-F046, PED-F004).
     * Null-safe, como {@link #isDevAccess}.
     */
    static boolean hasAuthority(Authentication auth, String authority) {
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
