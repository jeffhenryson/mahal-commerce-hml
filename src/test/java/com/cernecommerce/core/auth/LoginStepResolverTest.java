package com.cernecommerce.core.auth;

import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.PendingLoginPurpose;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.core.ports.out.token.PendingLoginTokenRepository;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.twofa.TwoFactorAuthPort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginStepResolverTest {

    @Mock UserRepository userRepository;
    @Mock AccessTokenPort accessToken;
    @Mock RefreshTokenPort refreshToken;
    @Mock TwoFactorAuthPort twoFactorAuth;
    @Mock SystemConfigPort systemConfig;
    @Mock PendingLoginTokenRepository pendingTokens;

    LoginStepResolver resolver;

    @BeforeEach
    void setUp() {
        lenient().when(systemConfig.getBoolean("security.2fa.required", false)).thenReturn(false);
        resolver = new LoginStepResolver(userRepository, accessToken, refreshToken, twoFactorAuth, systemConfig,
                pendingTokens);
    }

    private User operador() {
        return User.of("ana", "hash", Set.of());
    }

    @Test
    void sem_pendencia_emite_tokens() {
        when(accessToken.generateFor(eq("ana"), any())).thenReturn("a");
        when(refreshToken.issue("ana")).thenReturn("r");

        LoginResponse login = resolver.next(operador(), Set.of(), true);

        assertThat(login.tokenPair().getUsername()).isEqualTo("ana");
    }

    @Test
    void senha_temporaria_vem_antes_do_2fa_exigido() {
        User user = operador();
        user.setTemporaryPassword("hash");
        user.requireTwoFactor(true);

        LoginResponse login = resolver.next(user, Set.of(), true);

        assertThat(login.passwordChangeToken()).isNotBlank();
        assertThat(login.enrollmentToken()).isNull();
        verify(pendingTokens).save(eq("ana"), anyString(), eq(PendingLoginPurpose.PASSWORD_CHANGE), any());
        verifyNoInteractions(accessToken, refreshToken);
    }

    @Test
    void depois_da_troca_de_senha_o_2fa_exigido_continua_valendo() {
        User user = operador();
        user.requireTwoFactor(true);

        LoginResponse login = resolver.next(user, Set.of(), false);

        assertThat(login.enrollmentToken()).isNotBlank();
        verify(pendingTokens).save(eq("ana"), anyString(), eq(PendingLoginPurpose.TOTP_ENROLLMENT), any());
    }

    @Test
    void chave_global_nao_tranca_cliente_da_loja() {
        lenient().when(systemConfig.getBoolean("security.2fa.required", false)).thenReturn(true);
        lenient().when(accessToken.generateFor(eq("cliente@x.com"), any())).thenReturn("a");
        lenient().when(refreshToken.issue("cliente@x.com")).thenReturn("r");
        User cliente = User.customer("cliente@x.com", "hash", "cliente@x.com", 9L, Set.of());

        assertThat(resolver.next(cliente, Set.of(), true).tokenPair()).isNotNull();
    }

    @Test
    void chave_global_exige_cadastro_do_operador_sem_2fa() {
        when(systemConfig.getBoolean("security.2fa.required", false)).thenReturn(true);

        assertThat(resolver.next(operador(), Set.of(), true).enrollmentToken()).isNotBlank();
    }
}
