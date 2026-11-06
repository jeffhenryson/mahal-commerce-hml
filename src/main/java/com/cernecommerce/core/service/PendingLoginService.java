package com.cernecommerce.core.service;

import com.cernecommerce.core.auth.LoginStepResolver;
import com.cernecommerce.core.domain.PasswordPolicy;
import com.cernecommerce.core.domain.exception.auth.InvalidPasswordException;
import com.cernecommerce.core.domain.exception.auth.PendingLoginTokenInvalidException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.PendingLoginPurpose;
import com.cernecommerce.core.domain.model.auth.PendingLoginToken;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.PendingLoginUseCase;
import com.cernecommerce.core.ports.in.TotpUseCase;
import com.cernecommerce.core.ports.out.credential.PasswordHashPort;
import com.cernecommerce.core.ports.out.token.PendingLoginTokenRepository;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public class PendingLoginService implements PendingLoginUseCase {

    private final PendingLoginTokenRepository pendingTokens;
    private final UserRepository userRepository;
    private final UserAuthoritiesPort userAuthorities;
    private final UserCachePort userCachePort;
    private final PasswordHashPort passwordHash;
    private final TotpUseCase totp;
    private final LoginStepResolver loginStep;

    public PendingLoginService(PendingLoginTokenRepository pendingTokens, UserRepository userRepository,
            UserAuthoritiesPort userAuthorities, UserCachePort userCachePort, PasswordHashPort passwordHash,
            TotpUseCase totp, LoginStepResolver loginStep) {
        this.pendingTokens = pendingTokens;
        this.userRepository = userRepository;
        this.userAuthorities = userAuthorities;
        this.userCachePort = userCachePort;
        this.passwordHash = passwordHash;
        this.totp = totp;
        this.loginStep = loginStep;
    }

    @Override
    @Transactional
    public LoginResponse changeRequiredPassword(String passwordChangeToken, String newPassword) {
        PendingLoginToken pending = require(passwordChangeToken, PendingLoginPurpose.PASSWORD_CHANGE);
        String username = pending.username();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(username));
        // Senha recusada não gasta o token: a pessoa corrige e tenta de novo na mesma tela.
        if (!PasswordPolicy.isValid(newPassword) || passwordHash.matches(newPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }
        consume(passwordChangeToken);
        user.changePassword(passwordHash.hash(newPassword));
        User saved = userRepository.save(user);
        userCachePort.evict(username);
        return loginStep.next(saved, userAuthorities.loadAuthoritiesByUsername(username), false);
    }

    @Override
    @Transactional
    public TotpUseCase.TotpSetupResult startEnrollment(String enrollmentToken) {
        return totp.setup(require(enrollmentToken, PendingLoginPurpose.TOTP_ENROLLMENT).username());
    }

    @Override
    @Transactional
    public EnrollmentResult confirmEnrollment(String enrollmentToken, String totpCode) {
        String username = require(enrollmentToken, PendingLoginPurpose.TOTP_ENROLLMENT).username();
        // Código errado não gasta o token, como na senha.
        List<String> backupCodes = totp.confirm(username, totpCode);
        consume(enrollmentToken);
        LoginResponse login = loginStep.next(username, userAuthorities.loadAuthoritiesByUsername(username), false);
        return new EnrollmentResult(backupCodes, login.tokenPair());
    }

    private PendingLoginToken require(String rawToken, PendingLoginPurpose purpose) {
        return pendingTokens.findByToken(rawToken)
                .filter(token -> token.purpose() == purpose && token.isUsable(Instant.now()))
                .orElseThrow(PendingLoginTokenInvalidException::new);
    }

    private void consume(String rawToken) {
        if (!pendingTokens.markAsUsed(rawToken)) {
            throw new PendingLoginTokenInvalidException();
        }
    }
}
