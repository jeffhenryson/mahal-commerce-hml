package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.auth.AccountLockedException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.cernecommerce.core.auth.LoginStepResolver;
import com.cernecommerce.core.domain.model.auth.DevElevationResult;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.SessionInfo;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.core.ports.out.credential.CredentialVerifierPort;
import com.cernecommerce.core.ports.out.ratelimit.LoginAttemptPort;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import com.cernecommerce.core.ports.out.twofa.TwoFactorAuthPort;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AuthService implements AuthUseCase {

    private final CredentialVerifierPort credentialVerifier;
    private final AccessTokenPort accessToken;
    private final RefreshTokenPort refreshToken;
    private final UserAuthoritiesPort userAuthorities;
    private final TokenBlocklistPort tokenBlocklist;
    private final LoginAttemptPort loginAttempt;
    private final TwoFactorAuthPort twoFactorAuth;
    private final LoginStepResolver loginStep;

    public AuthService(CredentialVerifierPort credentialVerifier,
                       AccessTokenPort accessToken,
                       RefreshTokenPort refreshToken,
                       UserAuthoritiesPort userAuthorities,
                       TokenBlocklistPort tokenBlocklist,
                       LoginAttemptPort loginAttempt,
                       TwoFactorAuthPort twoFactorAuth,
                       LoginStepResolver loginStep) {
        this.credentialVerifier = credentialVerifier;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userAuthorities = userAuthorities;
        this.tokenBlocklist = tokenBlocklist;
        this.loginAttempt = loginAttempt;
        this.twoFactorAuth = twoFactorAuth;
        this.loginStep = loginStep;
    }

    @Override
    public LoginResponse login(String username, String password) {
        if (loginAttempt.isLocked(username)) {
            throw new AccountLockedException(username);
        }
        CredentialVerifierPort.VerifiedUser verified;
        try {
            verified = credentialVerifier.verify(username, password);
            loginAttempt.recordSuccess(username);
        } catch (RuntimeException ex) {
            loginAttempt.recordFailure(username);
            throw ex;
        }

        // A senha estava certa: o que falhar daqui em diante não conta como tentativa errada.
        if (twoFactorAuth.isEnabled(verified.username())) {
            return LoginResponse.twoFactorChallenge(twoFactorAuth.issueChallengeToken(verified.username()));
        }
        return loginStep.next(verified.username(), verified.authorities(), true);
    }

    @Override
    public LoginResponse completeTwoFactorLogin(String challengeToken, String totpCode) {
        String username = twoFactorAuth.completeChallengeLogin(challengeToken, totpCode);
        return loginStep.next(username, userAuthorities.loadAuthoritiesByUsername(username), true);
    }

    @Override
    public void recordLogin(String username, String ip) {
        loginStep.recordLogin(username, ip);
    }

    @Override
    public TokenPair refresh(String oldRefreshToken) {
        try {
            RefreshTokenPort.RotationResult result = refreshToken.rotate(oldRefreshToken);
            Set<String> authorities = userAuthorities.loadAuthoritiesByUsername(result.username());
            String access = accessToken.generateFor(result.username(), authorities);
            return new TokenPair(access, result.newToken());
        } catch (RefreshTokenAlreadyUsedException ex) {
            // Token reuse detected: revoke all sessions for this user before re-throwing.
            refreshToken.revokeAll(ex.getUsername());
            tokenBlocklist.blockAllBefore(ex.getUsername(), Instant.now());
            throw ex;
        }
    }

    @Override
    public void logout(String refreshTokenValue) {
        refreshToken.revoke(refreshTokenValue)
                .ifPresent(username -> tokenBlocklist.blockAllBefore(username, Instant.now()));
    }

    @Override
    public void logoutAll(String username) {
        refreshToken.revokeAll(username);
        tokenBlocklist.blockAllBefore(username, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionInfo> listActiveSessions(String username) {
        return refreshToken.findActiveSessions(username);
    }

    @Override
    public void revokeSession(Long sessionId, String username) {
        refreshToken.revokeByIdForUser(sessionId, username);
    }

    @Override
    public DevElevationResult completeDevElevation(String rawDevToken, String secondTotpCode) {
        String username = twoFactorAuth.completeDevChallenge(rawDevToken, secondTotpCode);
        Set<String> authorities = new HashSet<>(userAuthorities.loadAuthoritiesByUsername(username));
        // DEV_ELEVATED sinaliza que o duplo TOTP foi concluído — protege endpoints da área DEV.
        authorities.add("DEV_ELEVATED");
        String devAccessToken = accessToken.generateFor(username, authorities);
        return new DevElevationResult(username, devAccessToken);
    }
}
