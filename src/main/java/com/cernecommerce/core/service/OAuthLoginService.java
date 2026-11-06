package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.auth.GoogleAccountNotRegisteredException;
import com.cernecommerce.core.domain.exception.auth.OAuthTokenInvalidException;
import com.cernecommerce.core.domain.model.auth.GoogleUserInfo;
import com.cernecommerce.core.domain.model.auth.OAuthLoginResult;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.OAuthLoginUseCase;
import com.cernecommerce.core.ports.out.oauth.GoogleTokenVerifierPort;
import com.cernecommerce.core.auth.LoginStepResolver;
import com.cernecommerce.core.ports.out.twofa.TwoFactorAuthPort;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;
import com.cernecommerce.core.domain.exception.auth.AccountDisabledException;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

public class OAuthLoginService implements OAuthLoginUseCase {

    private final GoogleTokenVerifierPort tokenVerifier;
    private final UserRepository userRepository;
    private final UserAuthoritiesPort userAuthorities;
    private final UserCachePort userCachePort;
    private final TwoFactorAuthPort twoFactorAuth;
    private final LoginStepResolver loginStep;

    public OAuthLoginService(GoogleTokenVerifierPort tokenVerifier,
                             UserRepository userRepository,
                             UserAuthoritiesPort userAuthorities,
                             UserCachePort userCachePort,
                             TwoFactorAuthPort twoFactorAuth,
                             LoginStepResolver loginStep) {
        this.tokenVerifier = tokenVerifier;
        this.userRepository = userRepository;
        this.userAuthorities = userAuthorities;
        this.userCachePort = userCachePort;
        this.twoFactorAuth = twoFactorAuth;
        this.loginStep = loginStep;
    }

    @Override
    @Transactional
    public OAuthLoginResult loginWithGoogle(String idToken) {
        GoogleUserInfo googleInfo;
        try {
            googleInfo = tokenVerifier.verify(idToken);
        } catch (OAuthTokenInvalidException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OAuthTokenInvalidException("Token Google inválido ou expirado");
        }

        // Sem vínculo prévio, este login cria a conta ou vincula o Google a ela: é o que vira alerta.
        Optional<User> linked = userRepository.findByGoogleId(googleInfo.googleId());
        boolean firstGoogleLogin = linked.isEmpty();
        User user = linked.orElseGet(() -> resolveUnlinkedUser(googleInfo));

        if (!user.isEnabled()) {
            throw new AccountDisabledException(user.getUsername());
        }

        // Mesmas exigências do login por senha, menos a troca de senha, que aqui não se aplica.
        LoginResponse login = twoFactorAuth.isEnabled(user.getUsername())
                ? LoginResponse.twoFactorChallenge(twoFactorAuth.issueChallengeToken(user.getUsername()))
                : loginStep.next(user, userAuthorities.loadAuthoritiesByUsername(user.getUsername()), false);

        return new OAuthLoginResult(login, user.getUsername(), firstGoogleLogin);
    }

    @Override
    public void recordLogin(String username, String ip) {
        loginStep.recordLogin(username, ip);
    }

    /** Google ID ainda sem conta vinculada (o caso "já vinculada" é resolvido por quem chama). */
    private User resolveUnlinkedUser(GoogleUserInfo info) {
        String normalizedEmail = normalizeEmail(info.email());

        // 2. Conta local com mesmo email — vincula automaticamente
        Optional<User> byEmail = userRepository.findByEmail(normalizedEmail);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.linkGoogle(info.googleId());
            User saved = userRepository.save(existing);
            userCachePort.evict(saved.getUsername());
            return saved;
        }

        // 3. Sem conta: o login Google não cria usuários — só dev/admin cadastram.
        throw new GoogleAccountNotRegisteredException();
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.strip().toLowerCase();
    }
}
