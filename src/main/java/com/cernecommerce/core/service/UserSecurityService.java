package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.PasswordPolicy;
import com.cernecommerce.core.domain.exception.auth.InvalidPasswordException;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.InviteEmailRequiredException;
import com.cernecommerce.core.domain.exception.user.TargetProtectedException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;
import com.cernecommerce.core.domain.model.auth.SessionInfo;
import com.cernecommerce.core.domain.model.auth.TotpConfig;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.auth.UserSecurityInfo;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.in.UserSecurityUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.out.credential.PasswordHashPort;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import com.cernecommerce.core.ports.out.twofa.TotpBackupCodeRepository;
import com.cernecommerce.core.ports.out.twofa.TotpConfigRepository;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public class UserSecurityService implements UserSecurityUseCase {

    private final UserRepository userRepository;
    private final UserUseCase userUseCase;
    private final AuthUseCase authUseCase;
    private final PasswordHashPort passwordHash;
    private final RefreshTokenPort refreshToken;
    private final TokenBlocklistPort tokenBlocklist;
    private final TotpConfigRepository totpConfigRepository;
    private final TotpBackupCodeRepository totpBackupCodeRepository;
    private final UserCachePort userCachePort;
    private final EmailPort emailPort;

    public UserSecurityService(UserRepository userRepository, UserUseCase userUseCase, AuthUseCase authUseCase,
            PasswordHashPort passwordHash, RefreshTokenPort refreshToken, TokenBlocklistPort tokenBlocklist,
            TotpConfigRepository totpConfigRepository, TotpBackupCodeRepository totpBackupCodeRepository,
            UserCachePort userCachePort, EmailPort emailPort) {
        this.userRepository = userRepository;
        this.userUseCase = userUseCase;
        this.authUseCase = authUseCase;
        this.passwordHash = passwordHash;
        this.refreshToken = refreshToken;
        this.tokenBlocklist = tokenBlocklist;
        this.totpConfigRepository = totpConfigRepository;
        this.totpBackupCodeRepository = totpBackupCodeRepository;
        this.userCachePort = userCachePort;
        this.emailPort = emailPort;
    }

    @Override
    public String sendPasswordReset(Long id) {
        User user = require(id);
        if (user.getEmail() == null) {
            throw new InviteEmailRequiredException();
        }
        userUseCase.requestPasswordReset(user.getEmail());
        return user.getUsername();
    }

    @Override
    @Transactional
    public String setTemporaryPassword(Long id, String temporaryPassword) {
        if (!PasswordPolicy.isValid(temporaryPassword)) {
            throw new InvalidPasswordException();
        }
        User user = require(id);
        user.setTemporaryPassword(passwordHash.hash(temporaryPassword));
        userRepository.save(user);
        endSessions(user.getUsername());
        return user.getUsername();
    }

    @Override
    @Transactional(readOnly = true)
    public UserSecurityInfo getSecurity(Long id) {
        User user = require(id);
        String username = user.getUsername();
        boolean totpEnabled = totpConfigRepository.findByUsername(username).map(TotpConfig::enabled).orElse(false);
        return new UserSecurityInfo(totpEnabled, user.isTwoFactorRequired(),
                totpEnabled ? totpBackupCodeRepository.countRemainingByUsername(username) : 0,
                user.isMustChangePassword(), user.getLastLoginAt(), user.getLastLoginIp());
    }

    @Override
    @Transactional
    public String setTwoFactorRequired(Long id, boolean required) {
        User user = require(id);
        boolean turningOn = required && !user.isTwoFactorRequired();
        user.requireTwoFactor(required);
        userRepository.save(user);
        userCachePort.evict(user.getUsername());
        if (turningOn && user.getEmail() != null) {
            emailPort.sendTwoFactorRequired(user.getEmail(), user.getUsername());
        }
        return user.getUsername();
    }

    @Override
    @Transactional
    public String resetTwoFactor(Long id) {
        User user = require(id);
        totpConfigRepository.deleteByUsername(user.getUsername());
        totpBackupCodeRepository.deleteByUsername(user.getUsername());
        endSessions(user.getUsername());
        return user.getUsername();
    }

    @Override
    public List<SessionInfo> listSessions(Long id) {
        return authUseCase.listActiveSessions(require(id).getUsername());
    }

    @Override
    @Transactional
    public String revokeSessions(Long id) {
        String username = require(id).getUsername();
        endSessions(username);
        return username;
    }

    @Override
    public String revokeSession(Long id, Long sessionId) {
        String username = require(id).getUsername();
        authUseCase.revokeSession(sessionId, username);
        return username;
    }

    @Override
    @Transactional
    public String forceEmail(Long id, String email) {
        String normalized = email.strip().toLowerCase();
        User user = require(id);
        userRepository.findByEmail(normalized).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new EmailAlreadyExistsException(normalized);
            }
        });
        String previous = user.getEmail();
        user.changeEmail(normalized);
        user.markEmailVerified();
        userRepository.save(user);
        userCachePort.evict(user.getUsername());
        if (previous != null && !previous.equalsIgnoreCase(normalized)) {
            emailPort.sendEmailChangedByAdmin(previous, user.getUsername(), SecurityEventContext.at(Instant.now()));
        }
        return user.getUsername();
    }

    @Override
    public String usernameOf(Long id) {
        return require(id).getUsername();
    }

    @Override
    @Transactional
    public String restore(Long id, boolean allowDevAccount) {
        User deleted = userRepository.findDeletedById(id).orElseThrow(() -> new UserNotFoundException(id));
        boolean devAccount = deleted.getRoles().stream().anyMatch(role -> "ROLE_DEV".equals(role.getName()));
        if (devAccount && !allowDevAccount) {
            throw new TargetProtectedException();
        }
        userRepository.findByUsername(deleted.getUsername()).ifPresent(other -> {
            throw new UsernameAlreadyExistsException(deleted.getUsername());
        });
        if (deleted.getEmail() != null) {
            userRepository.findByEmail(deleted.getEmail()).ifPresent(other -> {
                throw new EmailAlreadyExistsException(deleted.getEmail());
            });
        }
        userRepository.restoreDeleted(id);
        return deleted.getUsername();
    }

    private User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private void endSessions(String username) {
        refreshToken.revokeAll(username);
        tokenBlocklist.blockAllBefore(username, Instant.now());
        userCachePort.evict(username);
    }
}
