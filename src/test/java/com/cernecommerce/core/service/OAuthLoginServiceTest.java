package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.auth.OAuthTokenInvalidException;
import com.cernecommerce.core.domain.model.auth.AuthProvider;
import com.cernecommerce.core.domain.model.auth.GoogleUserInfo;
import com.cernecommerce.core.domain.model.auth.OAuthLoginResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.out.oauth.GoogleTokenVerifierPort;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cernecommerce.core.domain.exception.auth.AccountDisabledException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthLoginServiceTest {

    @Mock GoogleTokenVerifierPort tokenVerifier;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock AccessTokenPort accessTokenPort;
    @Mock RefreshTokenPort refreshTokenPort;
    @Mock UserAuthoritiesPort userAuthoritiesPort;
    @Mock UserCachePort userCachePort;

    OAuthLoginService service;

    private static final GoogleUserInfo GOOGLE_INFO =
            new GoogleUserInfo("google-123", "Alice@Example.COM", "Alice");

    @BeforeEach
    void setUp() {
        service = new OAuthLoginService(tokenVerifier, userRepository, roleRepository,
                accessTokenPort, refreshTokenPort, userAuthoritiesPort, userCachePort);
    }

    // ── happy paths ───────────────────────────────────────────────────────────

    @Test
    void loginWithGoogle_returnsTokens_whenUserFoundByGoogleId() {
        User user = User.fromPersisted(1L, "alice", null, true, "alice@example.com",
                true, null, null, null, Set.of(), "google-123", AuthProvider.GOOGLE);
        when(tokenVerifier.verify(anyString())).thenReturn(GOOGLE_INFO);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(user));
        when(userAuthoritiesPort.loadAuthoritiesByUsername("alice")).thenReturn(Set.of("ROLE_USER"));
        when(accessTokenPort.generateFor(eq("alice"), any())).thenReturn("access-token");
        when(refreshTokenPort.issue("alice")).thenReturn("refresh-token");

        OAuthLoginResult result = service.loginWithGoogle("id-token");

        assertThat(result.tokenPair().getAccessToken()).isEqualTo("access-token");
        assertThat(result.tokenPair().getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.firstGoogleLogin()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_linksGoogleId_whenLocalAccountMatchesByEmail() {
        User localUser = User.fromPersisted(1L, "alice", "hashed", true, "alice@example.com",
                true, null, null, null, Set.of(), null, AuthProvider.LOCAL);
        when(tokenVerifier.verify(anyString())).thenReturn(GOOGLE_INFO);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(localUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userAuthoritiesPort.loadAuthoritiesByUsername("alice")).thenReturn(Set.of("ROLE_USER"));
        when(accessTokenPort.generateFor(anyString(), any())).thenReturn("access-token");
        when(refreshTokenPort.issue(anyString())).thenReturn("refresh-token");

        OAuthLoginResult linked = service.loginWithGoogle("id-token");

        assertThat(linked.firstGoogleLogin()).isTrue();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getGoogleId()).isEqualTo("google-123");
    }

    @Test
    void loginWithGoogle_createsNewUser_whenNoMatchFound() {
        when(tokenVerifier.verify(anyString())).thenReturn(GOOGLE_INFO);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userAuthoritiesPort.loadAuthoritiesByUsername(anyString())).thenReturn(Set.of("ROLE_USER"));
        when(accessTokenPort.generateFor(anyString(), any())).thenReturn("access-token");
        when(refreshTokenPort.issue(anyString())).thenReturn("refresh-token");

        service.loginWithGoogle("id-token");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User newUser = saved.getValue();
        assertThat(newUser.getGoogleId()).isEqualTo("google-123");
        assertThat(newUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(newUser.isEmailVerified()).isTrue();
        assertThat(newUser.isEnabled()).isTrue();
    }

    // ── email normalization ───────────────────────────────────────────────────

    @Test
    void loginWithGoogle_normalizesEmailToLowercase() {
        User user = User.fromPersisted(1L, "alice", null, true, "alice@example.com",
                true, null, null, null, Set.of(), "google-123", AuthProvider.GOOGLE);
        when(tokenVerifier.verify(anyString())).thenReturn(GOOGLE_INFO); // email = "Alice@Example.COM"
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userAuthoritiesPort.loadAuthoritiesByUsername(anyString())).thenReturn(Set.of());
        when(accessTokenPort.generateFor(anyString(), any())).thenReturn("t");
        when(refreshTokenPort.issue(anyString())).thenReturn("r");

        service.loginWithGoogle("id-token");

        verify(userRepository).findByEmail("alice@example.com");
    }

    @Test
    void loginWithGoogle_newUser_storesNormalizedEmail() {
        when(tokenVerifier.verify(anyString())).thenReturn(GOOGLE_INFO); // email = "Alice@Example.COM"
        when(userRepository.findByGoogleId(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userAuthoritiesPort.loadAuthoritiesByUsername(anyString())).thenReturn(Set.of());
        when(accessTokenPort.generateFor(anyString(), any())).thenReturn("t");
        when(refreshTokenPort.issue(anyString())).thenReturn("r");

        service.loginWithGoogle("id-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
    }

    // ── error paths ───────────────────────────────────────────────────────────

    @Test
    void loginWithGoogle_throwsOAuthTokenInvalid_whenVerifierFails() {
        when(tokenVerifier.verify(anyString())).thenThrow(new OAuthTokenInvalidException("bad token"));

        assertThatThrownBy(() -> service.loginWithGoogle("bad-token"))
                .isInstanceOf(OAuthTokenInvalidException.class);
    }

    @Test
    void loginWithGoogle_throwsOAuthTokenInvalid_whenVerifierThrowsGenericException() {
        when(tokenVerifier.verify(anyString())).thenThrow(new RuntimeException("network error"));

        assertThatThrownBy(() -> service.loginWithGoogle("bad-token"))
                .isInstanceOf(OAuthTokenInvalidException.class);
    }

    @Test
    void loginWithGoogle_throwsDisabled_whenUserAccountIsDisabled() {
        User disabled = User.fromPersisted(1L, "alice", null, false, "alice@example.com",
                true, null, null, null, Set.of(), "google-123", AuthProvider.GOOGLE);
        when(tokenVerifier.verify(anyString())).thenReturn(GOOGLE_INFO);
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.loginWithGoogle("id-token"))
                .isInstanceOf(AccountDisabledException.class);
    }

    // ── edge case: google_id rotation ─────────────────────────────────────────

    @Test
    void loginWithGoogle_updatesGoogleId_whenUserSwitchesGoogleAccount() {
        // Usuário anteriormente fez login com uma conta Google (google-old)
        // Agora tenta login com uma conta Google diferente (google-123) mas mesmo email
        User userWithOldGoogleId = User.fromPersisted(1L, "alice", null, true, "alice@example.com",
                true, null, null, null, Set.of(), "google-old", AuthProvider.GOOGLE);
        when(tokenVerifier.verify(anyString())).thenReturn(GOOGLE_INFO); // googleId = "google-123"
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(userWithOldGoogleId));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userAuthoritiesPort.loadAuthoritiesByUsername("alice")).thenReturn(Set.of("ROLE_USER"));
        when(accessTokenPort.generateFor(eq("alice"), any())).thenReturn("access-token");
        when(refreshTokenPort.issue("alice")).thenReturn("refresh-token");

        OAuthLoginResult result = service.loginWithGoogle("id-token");

        // Confirma que o google_id foi atualizado
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getGoogleId()).isEqualTo("google-123");
        assertThat(saved.getValue().getUsername()).isEqualTo("alice"); // username não muda
        assertThat(result.username()).isEqualTo("alice");
    }
}
