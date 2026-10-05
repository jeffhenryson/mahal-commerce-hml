package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.auth.OAuthTokenInvalidException;
import com.cernecommerce.core.domain.model.auth.GoogleUserInfo;
import com.cernecommerce.core.domain.model.auth.OAuthLoginResult;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.in.OAuthLoginUseCase;
import com.cernecommerce.core.ports.out.oauth.GoogleTokenVerifierPort;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;
import com.cernecommerce.core.domain.exception.auth.AccountDisabledException;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class OAuthLoginService implements OAuthLoginUseCase {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final GoogleTokenVerifierPort tokenVerifier;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccessTokenPort accessToken;
    private final RefreshTokenPort refreshToken;
    private final UserAuthoritiesPort userAuthorities;
    private final UserCachePort userCachePort;

    public OAuthLoginService(GoogleTokenVerifierPort tokenVerifier,
                             UserRepository userRepository,
                             RoleRepository roleRepository,
                             AccessTokenPort accessToken,
                             RefreshTokenPort refreshToken,
                             UserAuthoritiesPort userAuthorities,
                             UserCachePort userCachePort) {
        this.tokenVerifier = tokenVerifier;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userAuthorities = userAuthorities;
        this.userCachePort = userCachePort;
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

        Set<String> authorities = userAuthorities.loadAuthoritiesByUsername(user.getUsername());
        String access = accessToken.generateFor(user.getUsername(), authorities);
        String refresh = refreshToken.issue(user.getUsername());

        return new OAuthLoginResult(new TokenPair(access, refresh), user.getUsername(), firstGoogleLogin);
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

        // 3. Novo usuário — cria com ROLE_USER e email já verificado
        String username = generateUniqueUsername(normalizedEmail);
        Set<Role> roles = resolveDefaultRoles();
        User newUser = User.fromGoogle(info.googleId(), username, normalizedEmail, roles);
        User saved = userRepository.save(newUser);
        userCachePort.evict(saved.getUsername());
        return saved;
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.strip().toLowerCase();
    }

    private String generateUniqueUsername(String email) {
        String base = email.split("@")[0]
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        if (base.length() < 3) base = "user";
        if (base.length() > 60) base = base.substring(0, 60);

        String candidate = base;
        int suffix = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private Set<Role> resolveDefaultRoles() {
        Set<Role> roles = new HashSet<>();
        roleRepository.findByName(DEFAULT_ROLE).ifPresent(roles::add);
        return roles;
    }
}
