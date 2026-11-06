package com.cernecommerce.core.auth;

import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.PendingLoginPurpose;
import com.cernecommerce.core.domain.model.auth.PendingLoginToken;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.core.ports.out.token.PendingLoginTokenRepository;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.twofa.TwoFactorAuthPort;
import com.cernecommerce.core.ports.out.user.UserRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

/**
 * O que acontece depois que a identidade foi provada (senha, código de 2FA ou Google). Um lugar só, para
 * que nenhuma porta de entrada emita tokens pulando uma exigência: o login com Google fazia isso.
 *
 * <p>Fora de {@code core.service} de propósito: lá toda classe é um caso de uso.
 */
public final class LoginStepResolver {

    private static final String GLOBAL_2FA_KEY = "security.2fa.required";

    private final UserRepository userRepository;
    private final AccessTokenPort accessToken;
    private final RefreshTokenPort refreshToken;
    private final TwoFactorAuthPort twoFactorAuth;
    private final SystemConfigPort systemConfig;
    private final PendingLoginTokenRepository pendingTokens;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginStepResolver(UserRepository userRepository, AccessTokenPort accessToken,
            RefreshTokenPort refreshToken, TwoFactorAuthPort twoFactorAuth, SystemConfigPort systemConfig,
            PendingLoginTokenRepository pendingTokens) {
        this.userRepository = userRepository;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.twoFactorAuth = twoFactorAuth;
        this.systemConfig = systemConfig;
        this.pendingTokens = pendingTokens;
    }

    /** @see #next(User, Set, boolean) */
    public LoginResponse next(String username, Set<String> authorities, boolean checkPasswordChange) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        return next(user, authorities, checkPasswordChange);
    }

    /**
     * Quem chama já resolveu o desafio de 2FA de quem o tem ativo.
     *
     * @param checkPasswordChange {@code false} no Google, que não usa senha, e depois de a senha ter sido trocada
     */
    public LoginResponse next(User user, Set<String> authorities, boolean checkPasswordChange) {
        String username = user.getUsername();
        if (checkPasswordChange && user.isMustChangePassword()) {
            return LoginResponse.passwordChange(issuePending(username, PendingLoginPurpose.PASSWORD_CHANGE));
        }
        if (requiresTwoFactor(user) && !twoFactorAuth.isEnabled(username)) {
            return LoginResponse.totpEnrollment(issuePending(username, PendingLoginPurpose.TOTP_ENROLLMENT));
        }
        String access = accessToken.generateFor(username, authorities);
        String refresh = refreshToken.issue(username);
        return LoginResponse.success(new TokenPair(access, refresh, username));
    }

    /** Grava o último acesso. Só no login, não no refresh. */
    public void recordLogin(String username, String ip) {
        userRepository.recordLogin(username, Instant.now(), ip);
    }

    /**
     * A chave global vale para operadores: o app do cliente não tem tela de cadastro de 2FA, e o cliente
     * ficaria sem entrar. A exigência individual vale para qualquer conta.
     */
    private boolean requiresTwoFactor(User user) {
        return user.isTwoFactorRequired()
                || (!user.isCustomer() && systemConfig.getBoolean(GLOBAL_2FA_KEY, false));
    }

    private String issuePending(String username, PendingLoginPurpose purpose) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingTokens.save(username, rawToken, purpose, Instant.now().plusSeconds(PendingLoginToken.TTL_SECONDS));
        return rawToken;
    }
}
