package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.auth.InvalidPasswordException;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.InviteEmailRequiredException;
import com.cernecommerce.core.domain.exception.user.TargetProtectedException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;
import com.cernecommerce.core.domain.model.auth.AuthProvider;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.out.credential.PasswordHashPort;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import com.cernecommerce.core.ports.out.twofa.TotpBackupCodeRepository;
import com.cernecommerce.core.ports.out.twofa.TotpConfigRepository;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSecurityServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserUseCase userUseCase;
    @Mock AuthUseCase authUseCase;
    @Mock PasswordHashPort passwordHash;
    @Mock RefreshTokenPort refreshToken;
    @Mock TokenBlocklistPort tokenBlocklist;
    @Mock TotpConfigRepository totpConfigRepository;
    @Mock TotpBackupCodeRepository totpBackupCodeRepository;
    @Mock UserCachePort userCachePort;
    @Mock EmailPort emailPort;

    UserSecurityService service;

    @BeforeEach
    void setUp() {
        service = new UserSecurityService(userRepository, userUseCase, authUseCase, passwordHash, refreshToken,
                tokenBlocklist, totpConfigRepository, totpBackupCodeRepository, userCachePort, emailPort);
    }

    private User conta(String email, Set<Role> roles) {
        return User.fromPersisted(1L, "ana", "hash", true, email, true, null, null, null, roles, null,
                AuthProvider.LOCAL);
    }

    private User existente(String email) {
        User user = conta(email, Set.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void redefinicao_sem_email_avisa() {
        existente(null);

        assertThatThrownBy(() -> service.sendPasswordReset(1L)).isInstanceOf(InviteEmailRequiredException.class);
    }

    @Test
    void senha_temporaria_obriga_a_troca_e_encerra_as_sessoes() {
        User user = existente("ana@x.com");
        when(passwordHash.hash("Tempor@ria1")).thenReturn("hash-temporaria");

        service.setTemporaryPassword(1L, "Tempor@ria1");

        assertThat(user.isMustChangePassword()).isTrue();
        verify(refreshToken).revokeAll("ana");
        verify(tokenBlocklist).blockAllBefore(eq("ana"), any());
    }

    @Test
    void senha_temporaria_fora_da_politica_e_recusada() {
        assertThatThrownBy(() -> service.setTemporaryPassword(1L, "fraca")).isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void exigir_2fa_avisa_so_na_primeira_vez() {
        User user = existente("ana@x.com");

        service.setTwoFactorRequired(1L, true);
        service.setTwoFactorRequired(1L, true);

        assertThat(user.isTwoFactorRequired()).isTrue();
        verify(emailPort, org.mockito.Mockito.times(1)).sendTwoFactorRequired("ana@x.com", "ana");
    }

    @Test
    void reset_de_2fa_nao_mexe_na_exigencia() {
        User user = existente("ana@x.com");
        user.requireTwoFactor(true);

        service.resetTwoFactor(1L);

        verify(totpConfigRepository).deleteByUsername("ana");
        verify(totpBackupCodeRepository).deleteByUsername("ana");
        assertThat(user.isTwoFactorRequired()).isTrue();
    }

    @Test
    void trocar_email_marca_verificado_e_avisa_o_antigo() {
        User user = existente("antigo@x.com");
        when(userRepository.findByEmail("novo@x.com")).thenReturn(Optional.empty());

        service.forceEmail(1L, " Novo@x.com ");

        assertThat(user.getEmail()).isEqualTo("novo@x.com");
        assertThat(user.isEmailVerified()).isTrue();
        verify(emailPort).sendEmailChangedByAdmin(eq("antigo@x.com"), eq("ana"), any());
    }

    @Test
    void email_de_outra_conta_e_recusado() {
        existente("antigo@x.com");
        User outra = User.fromPersisted(2L, "bia", "hash", true, "novo@x.com", true, null, null, null, Set.of(), null,
                AuthProvider.LOCAL);
        when(userRepository.findByEmail("novo@x.com")).thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> service.forceEmail(1L, "novo@x.com")).isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void restaurar_conta_de_dev_exige_acesso_elevado() {
        Role dev = Role.of(9L, "ROLE_DEV", Set.of());
        when(userRepository.findDeletedById(1L)).thenReturn(Optional.of(conta("ana@x.com", Set.of(dev))));

        assertThatThrownBy(() -> service.restore(1L, false)).isInstanceOf(TargetProtectedException.class);

        verify(userRepository, never()).restoreDeleted(any());
    }

    @Test
    void restaurar_com_nome_tomado_e_recusado() {
        when(userRepository.findDeletedById(1L)).thenReturn(Optional.of(conta("ana@x.com", Set.of())));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(conta("ana@x.com", Set.of())));

        assertThatThrownBy(() -> service.restore(1L, false)).isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void restaurar_conta_livre_devolve_o_nome() {
        when(userRepository.findDeletedById(1L)).thenReturn(Optional.of(conta("ana@x.com", Set.of())));
        when(userRepository.findByUsername("ana")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ana@x.com")).thenReturn(Optional.empty());

        assertThat(service.restore(1L, false)).isEqualTo("ana");

        verify(userRepository).restoreDeleted(1L);
    }
}
