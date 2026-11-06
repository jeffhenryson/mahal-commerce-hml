package com.cernecommerce.core.service;

import com.cernecommerce.core.auth.LoginStepResolver;
import com.cernecommerce.core.domain.exception.auth.InvalidPasswordException;
import com.cernecommerce.core.domain.exception.auth.PendingLoginTokenInvalidException;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.PendingLoginPurpose;
import com.cernecommerce.core.domain.model.auth.PendingLoginToken;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.PendingLoginUseCase;
import com.cernecommerce.core.ports.in.TotpUseCase;
import com.cernecommerce.core.ports.out.credential.PasswordHashPort;
import com.cernecommerce.core.ports.out.token.PendingLoginTokenRepository;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingLoginServiceTest {

    private static final String SENHA_BOA = "Nova@Senha1";

    @Mock PendingLoginTokenRepository pendingTokens;
    @Mock UserRepository userRepository;
    @Mock UserAuthoritiesPort userAuthorities;
    @Mock UserCachePort userCachePort;
    @Mock PasswordHashPort passwordHash;
    @Mock TotpUseCase totp;
    @Mock LoginStepResolver loginStep;

    PendingLoginService service;

    @BeforeEach
    void setUp() {
        service = new PendingLoginService(pendingTokens, userRepository, userAuthorities, userCachePort,
                passwordHash, totp, loginStep);
        lenient().when(userAuthorities.loadAuthoritiesByUsername("ana")).thenReturn(Set.of());
    }

    private void token(PendingLoginPurpose purpose, Instant expiresAt, Instant usedAt) {
        when(pendingTokens.findByToken("t")).thenReturn(Optional.of(
                new PendingLoginToken(1L, "ana", "hash", purpose, expiresAt, Instant.now(), usedAt)));
    }

    private static Instant futuro() {
        return Instant.now().plusSeconds(60);
    }

    @Test
    void token_de_outra_finalidade_e_recusado() {
        token(PendingLoginPurpose.TOTP_ENROLLMENT, futuro(), null);

        assertThatThrownBy(() -> service.changeRequiredPassword("t", SENHA_BOA))
                .isInstanceOf(PendingLoginTokenInvalidException.class);
    }

    @Test
    void token_vencido_ou_usado_e_recusado() {
        token(PendingLoginPurpose.TOTP_ENROLLMENT, Instant.now().minusSeconds(1), null);
        assertThatThrownBy(() -> service.startEnrollment("t")).isInstanceOf(PendingLoginTokenInvalidException.class);

        token(PendingLoginPurpose.TOTP_ENROLLMENT, futuro(), Instant.now());
        assertThatThrownBy(() -> service.startEnrollment("t")).isInstanceOf(PendingLoginTokenInvalidException.class);
    }

    @Test
    void troca_a_senha_limpa_a_pendencia_e_continua_o_login() {
        User user = User.of("ana", "hash-temporaria", Set.of());
        user.setTemporaryPassword("hash-temporaria");
        token(PendingLoginPurpose.PASSWORD_CHANGE, futuro(), null);
        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(user));
        when(passwordHash.matches(SENHA_BOA, "hash-temporaria")).thenReturn(false);
        when(passwordHash.hash(SENHA_BOA)).thenReturn("hash-nova");
        when(pendingTokens.markAsUsed("t")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        LoginResponse esperado = LoginResponse.success(new TokenPair("a", "r", "ana"));
        when(loginStep.next(eq(user), any(), eq(false))).thenReturn(esperado);

        assertThat(service.changeRequiredPassword("t", SENHA_BOA)).isEqualTo(esperado);
        assertThat(user.isMustChangePassword()).isFalse();
    }

    @Test
    void repetir_a_senha_temporaria_e_recusado_sem_gastar_o_token() {
        User user = User.of("ana", "hash-temporaria", Set.of());
        token(PendingLoginPurpose.PASSWORD_CHANGE, futuro(), null);
        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(user));
        when(passwordHash.matches(SENHA_BOA, "hash-temporaria")).thenReturn(true);

        assertThatThrownBy(() -> service.changeRequiredPassword("t", SENHA_BOA))
                .isInstanceOf(InvalidPasswordException.class);

        verify(pendingTokens, never()).markAsUsed(anyString());
    }

    @Test
    void confirmar_o_2fa_devolve_codigos_e_tokens() {
        token(PendingLoginPurpose.TOTP_ENROLLMENT, futuro(), null);
        when(totp.confirm("ana", "123456")).thenReturn(List.of("c1"));
        when(pendingTokens.markAsUsed("t")).thenReturn(true);
        TokenPair tokens = new TokenPair("a", "r", "ana");
        when(loginStep.next(eq("ana"), any(), anyBoolean())).thenReturn(LoginResponse.success(tokens));

        PendingLoginUseCase.EnrollmentResult result = service.confirmEnrollment("t", "123456");

        assertThat(result.backupCodes()).containsExactly("c1");
        assertThat(result.tokens()).isEqualTo(tokens);
    }

    @Test
    void segundo_uso_simultaneo_do_token_e_recusado() {
        token(PendingLoginPurpose.TOTP_ENROLLMENT, futuro(), null);
        when(totp.confirm("ana", "123456")).thenReturn(List.of("c1"));
        when(pendingTokens.markAsUsed("t")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmEnrollment("t", "123456"))
                .isInstanceOf(PendingLoginTokenInvalidException.class);
    }
}
