package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.auth.InvalidPasswordException;
import com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.InviteEmailRequiredException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;
import com.cernecommerce.core.domain.model.auth.EmailVerificationCode;
import com.cernecommerce.core.domain.model.auth.UpdateProfileResult;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.EmailVerificationCodeRepository;
import com.cernecommerce.core.ports.out.notification.PasswordResetTokenRepository;
import com.cernecommerce.core.ports.out.credential.PasswordHashPort;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import com.cernecommerce.core.domain.exception.auth.InvalidTotpCodeException;
import com.cernecommerce.core.domain.exception.auth.TotpCodeRequiredException;
import com.cernecommerce.core.ports.out.twofa.TotpBackupCodeRepository;
import com.cernecommerce.core.ports.out.twofa.TotpChallengeTokenRepository;
import com.cernecommerce.core.ports.out.twofa.TotpConfigRepository;
import com.cernecommerce.core.ports.out.twofa.TwoFactorAuthPort;
import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordHashPort passwordHash;
    @Mock RefreshTokenPort refreshTokenPort;
    @Mock TokenBlocklistPort tokenBlocklistPort;
    @Mock EmailPort emailPort;
    @Mock EmailVerificationCodeRepository verificationCodeRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock UserCachePort userCachePort;
    @Mock TotpConfigRepository totpConfigRepository;
    @Mock TotpBackupCodeRepository totpBackupCodeRepository;
    @Mock TotpChallengeTokenRepository totpChallengeTokenRepository;
    @Mock TwoFactorAuthPort twoFactorAuthPort;
    @Mock AvatarStoragePort avatarStoragePort;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository, passwordHash,
                refreshTokenPort, tokenBlocklistPort, emailPort, verificationCodeRepository,
                passwordResetTokenRepository, userCachePort,
                totpConfigRepository, totpBackupCodeRepository, totpChallengeTokenRepository,
                twoFactorAuthPort, avatarStoragePort, 15L, 60L, 15L, "http://localhost:3000/reset-password", 48L);
    }

    @Test
    void createUser_rejectsWeakPassword() {
        assertThatThrownBy(() -> userService.createUser("alice", "short", List.of()))
                .isInstanceOf(InvalidPasswordException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void createUser_rejectsDuplicateUsername() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(User.of("alice", "hashed", null)));

        assertThatThrownBy(() -> userService.createUser("alice", "Password@123", List.of()))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void createUser_rejectsUnknownRole() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser("alice", "Password@123", List.of("ROLE_UNKNOWN")))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void createUser_savesUserWithRole() {
        Role role = new Role("ROLE_USER");
        when(passwordHash.hash(anyString())).thenReturn("hashed");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser("alice", "Password@123", List.of("ROLE_USER"));

        verify(userRepository).save(any(User.class));
        verifyNoInteractions(emailPort);
    }

    /**
     * E-mail em branco é "sem e-mail": não consulta duplicidade nem grava {@code ""}, que colidiria no
     * índice único de e-mail no segundo usuário criado assim.
     */
    @Test
    void createUser_withBlankEmail_isCreatedWithoutEmail() {
        when(passwordHash.hash(anyString())).thenReturn("hashed");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.createUser("alice", "Password@123", "   ", List.of());

        assertThat(created.getEmail()).isNull();
        verify(userRepository, never()).findByEmail(any());
    }

    // ── convite (USER_INVITE) ─────────────────────────────────────────────────

    @Test
    void inviteUser_createsUserWithUnknownPassword_andSendsInviteLink() {
        when(passwordHash.hash(anyString())).thenReturn("hashed-random");
        when(userRepository.findByUsername("novo")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("novo@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.inviteUser("novo", "Novo@Test.com", List.of());

        assertThat(created.getPassword()).isEqualTo("hashed-random");
        verify(passwordResetTokenRepository).deleteByUsername("novo");
        verify(passwordResetTokenRepository).save(eq("novo"), anyString(),
                argThat((Instant expiresAt) -> expiresAt.isAfter(Instant.now().plusSeconds(47 * 3600))));
        verify(emailPort).sendUserInvite(eq("novo@test.com"), eq("novo"),
                argThat((String link) -> link.startsWith("http://localhost:3000/reset-password?token=")), eq(48L));
    }

    @Test
    void inviteUser_withoutEmail_isRejected() {
        assertThatThrownBy(() -> userService.inviteUser("novo", " ", List.of()))
                .isInstanceOf(InviteEmailRequiredException.class);
        verifyNoInteractions(userRepository, emailPort);
    }

    @Test
    void resendInvite_replacesTokenAndSendsAgain() {
        User user = User.of("novo", "hashed", null);
        user.assignEmail("novo@test.com");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        userService.resendInvite(7L);

        verify(passwordResetTokenRepository).deleteByUsername("novo");
        verify(emailPort).sendUserInvite(eq("novo@test.com"), eq("novo"), anyString(), eq(48L));
    }

    @Test
    void resendInvite_unknownUser_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resendInvite(99L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void registerUser_rejectsDuplicateEmail() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@test.com"))
                .thenReturn(Optional.of(User.of("other", "hashed", null)));

        assertThatThrownBy(() -> userService.registerUser("alice", "Password@123", "alice@test.com", List.of()))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void registerUser_createsDisabledUserAndSendsEmail() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordHash.hash(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationCodeRepository.save(anyString(), anyString(), any()))
                .thenReturn(new EmailVerificationCode(1L, "alice", "HASHED", Instant.now().plusSeconds(900), Instant.now(), false));

        User created = userService.registerUser("alice", "Password@123", "alice@test.com", List.of());

        verify(emailPort).sendVerificationCode(eq("alice@test.com"), eq("alice"), anyString());
        assert !created.isEnabled();
    }

    @Test
    void createCustomerAccount_rejectsWeakPassword() {
        assertThatThrownBy(() -> userService.createCustomerAccount("alice@test.com", "short", 1L))
                .isInstanceOf(InvalidPasswordException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void createCustomerAccount_rejectsWhenEmailAlreadyUsernameOfAnyone() {
        // Simula um operador cujo username já é esse email — a checagem é cruzada, não filtrada
        // por tipo, mesmo com a constraint composta (user_type, username) na V76.
        when(userRepository.findByUsername("joao@x.com"))
                .thenReturn(Optional.of(User.of("joao@x.com", "hashed", null)));

        assertThatThrownBy(() -> userService.createCustomerAccount("joao@x.com", "Password@123", 1L))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createCustomerAccount_rejectsWhenEmailAlreadyUsedByAnyone() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("maria@x.com"))
                .thenReturn(Optional.of(User.of("other", "hashed", null)));

        assertThatThrownBy(() -> userService.createCustomerAccount("maria@x.com", "Password@123", 1L))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createCustomerAccount_savesEnabledCustomerTypeUserWithRoleCustomer() {
        Role customerRole = new Role("ROLE_CUSTOMER");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordHash.hash(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.createCustomerAccount("maria@x.com", "Password@123", 42L);

        assertThat(created.isCustomer()).isTrue();
        assertThat(created.getCustomerId()).isEqualTo(42L);
        assertThat(created.getUsername()).isEqualTo("maria@x.com");
        assertThat(created.getEmail()).isEqualTo("maria@x.com");
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getRoles()).extracting(Role::getName).containsExactly("ROLE_CUSTOMER");
        verify(userCachePort).evict("maria@x.com");
    }

    @Test
    void changeOwnPassword_rejectsWrongCurrentPassword() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.changeOwnPassword("alice", "wrong", "NewPass@123", null, false))
                .isInstanceOf(InvalidPasswordException.class);
        verifyNoInteractions(refreshTokenPort, tokenBlocklistPort);
    }

    @Test
    void changeOwnPassword_withRevokeOtherSessions_invalidatesAllSessions() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("current", "hashed")).thenReturn(true);
        when(passwordHash.hash("NewPass@123")).thenReturn("newHashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(twoFactorAuthPort.isEnabled("alice")).thenReturn(false);

        userService.changeOwnPassword("alice", "current", "NewPass@123", null, true);

        verify(refreshTokenPort).revokeAll("alice");
        verify(tokenBlocklistPort).blockAllBefore(eq("alice"), any());
    }

    @Test
    void changeOwnPassword_withoutRevokeOtherSessions_keepsSessionsActive() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("current", "hashed")).thenReturn(true);
        when(passwordHash.hash("NewPass@123")).thenReturn("newHashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(twoFactorAuthPort.isEnabled("alice")).thenReturn(false);

        userService.changeOwnPassword("alice", "current", "NewPass@123", null, false);

        verifyNoInteractions(refreshTokenPort, tokenBlocklistPort);
    }

    @Test
    void changeOwnPassword_withTotp_requiresCodeWhenEnabled() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("current", "hashed")).thenReturn(true);
        when(twoFactorAuthPort.isEnabled("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.changeOwnPassword("alice", "current", "NewPass@123", null, false))
                .isInstanceOf(TotpCodeRequiredException.class);
    }

    @Test
    void changeOwnPassword_withTotp_rejectsInvalidCode() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("current", "hashed")).thenReturn(true);
        when(twoFactorAuthPort.isEnabled("alice")).thenReturn(true);
        when(twoFactorAuthPort.validateTotpCode("alice", "000000")).thenReturn(false);

        assertThatThrownBy(() -> userService.changeOwnPassword("alice", "current", "NewPass@123", "000000", false))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test
    void changeOwnPassword_withTotp_succeedsWithValidCode() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("current", "hashed")).thenReturn(true);
        when(passwordHash.hash("NewPass@123")).thenReturn("newHashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(twoFactorAuthPort.isEnabled("alice")).thenReturn(true);
        when(twoFactorAuthPort.validateTotpCode("alice", "123456")).thenReturn(true);

        userService.changeOwnPassword("alice", "current", "NewPass@123", "123456", false);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void getUserById_throwsWhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteUser_revokesSessionsAndEvictsCache() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(refreshTokenPort).revokeAll("alice");
        verify(tokenBlocklistPort).blockAllBefore(eq("alice"), any());
        verify(userRepository).deleteById(1L);
        verify(userCachePort).evict("alice");
    }

    @Test
    void deleteUser_throwsWhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(refreshTokenPort, tokenBlocklistPort, userCachePort);
    }

    @Test
    void assignRole_addsRoleAndEvictsCache() {
        User user = User.of("alice", "hashed", null);
        Role role = new Role("ROLE_MANAGER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_MANAGER")).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.assignRole("alice", "ROLE_MANAGER");

        verify(userRepository).save(any(User.class));
        verify(userCachePort).evict("alice");
    }

    @Test
    void assignRole_throwsWhenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.assignRole("ghost", "ROLE_USER"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void setUserEnabled_false_revokesSessionsAndEvictsCache() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setUserEnabled(1L, false);

        verify(refreshTokenPort).revokeAll("alice");
        verify(tokenBlocklistPort).blockAllBefore(eq("alice"), any());
        verify(userCachePort).evict("alice");
    }

    @Test
    void setUserEnabled_true_doesNotRevokeSessions() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setUserEnabled(1L, true);

        verifyNoInteractions(refreshTokenPort, tokenBlocklistPort);
        verify(userCachePort).evict("alice");
    }

    @Test
    void verifyEmail_confirmsAccountAndEvictsCache() {
        EmailVerificationCode code = new EmailVerificationCode(
                1L, "alice", "HASH", Instant.now().plusSeconds(900), Instant.now(), false);
        User user = User.ofPendingVerification("alice", "hashed", "alice@test.com", null);
        when(verificationCodeRepository.findByCode("ABC123")).thenReturn(Optional.of(code));
        when(verificationCodeRepository.markAsUsed("ABC123")).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.verifyEmail("ABC123");

        verify(userRepository).save(any(User.class));
        verify(userCachePort).evict("alice");
    }

    @Test
    void verifyEmail_throwsWhenCodeNotFound() {
        when(verificationCodeRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifyEmail("INVALID"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.email.EmailVerificationCodeNotFoundException.class);
    }

    @Test
    void verifyEmail_throwsWhenCASFails() {
        EmailVerificationCode code = new EmailVerificationCode(
                1L, "alice", "HASH", Instant.now().plusSeconds(900), Instant.now(), false);
        User user = User.ofPendingVerification("alice", "hashed", "alice@test.com", null);
        when(verificationCodeRepository.findByCode("ABC123")).thenReturn(Optional.of(code));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(verificationCodeRepository.markAsUsed("ABC123")).thenReturn(false);

        assertThatThrownBy(() -> userService.verifyEmail("ABC123"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.email.EmailVerificationCodeExpiredException.class);
    }

    @Test
    void removeRole_removesRoleAndEvictsCache() {
        Role role = new Role("ROLE_MANAGER");
        User user = User.of("alice", "hashed", java.util.Set.of(role));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_MANAGER")).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.removeRole("alice", "ROLE_MANAGER");

        verify(userRepository).save(any(User.class));
        verify(userCachePort).evict("alice");
    }

    @Test
    void removeRole_throwsWhenRoleNotFound() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.removeRole("alice", "ROLE_UNKNOWN"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException.class);
    }

    @Test
    void updateOwnProfile_emailChange_requiresCurrentPassword() {
        User user = User.fromPersisted(1L, "alice", "hashed", true, "alice@old.com", true, null, null, null, java.util.Set.of(), null, null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("wrongpass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateOwnProfile("alice", "alice", "alice@new.com", "wrongpass"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.auth.InvalidPasswordException.class);
    }

    @Test
    void updateOwnProfile_emailChange_setsPendingEmailAndSendsCode() {
        User user = User.fromPersisted(1L, "alice", "hashed", true, "alice@old.com", true, null, null, null, java.util.Set.of(), null, null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHash.matches("current", "hashed")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("alice@new.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationCodeRepository.save(anyString(), anyString(), any()))
                .thenReturn(new EmailVerificationCode(1L, "alice", "HASH", Instant.now().plusSeconds(900), Instant.now(), false));

        UpdateProfileResult result = userService.updateOwnProfile("alice", "alice", "alice@new.com", "current");

        assertThat(result.user().getPendingEmail()).isEqualTo("alice@new.com");
        assertThat(result.emailChangePending()).isTrue();
        verify(emailPort).sendVerificationCode(eq("alice@new.com"), eq("alice"), anyString());
        verify(userCachePort, atLeastOnce()).evict("alice");
    }

    @Test
    void deleteUser_cleansUpAllTotpData() {
        User user = User.fromPersisted(1L, "alice", "hashed", true, null, false, null, null, null, java.util.Set.of(), null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(totpChallengeTokenRepository).deleteByUsername("alice");
        verify(totpBackupCodeRepository).deleteByUsername("alice");
        verify(totpConfigRepository).deleteByUsername("alice");
        verify(userRepository).deleteById(1L);
    }

    @Test
    void updateUser_usernameChange_revokesSessionsOfOldUsername() {
        User user = User.fromPersisted(1L, "alice", "hashed", true, null, false, null, null, null, java.util.Set.of(), null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(1L, "bob", null);

        verify(refreshTokenPort).revokeAll("alice");
        verify(tokenBlocklistPort).blockAllBefore(eq("alice"), any());
        verify(userCachePort).evict("alice");
    }

    @Test
    void updateUser_emailChange_setsPendingEmailWithoutDisablingAccount() {
        User user = User.fromPersisted(1L, "alice", "hashed", true, "alice@old.com", true, null, null, null, java.util.Set.of(), null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@new.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationCodeRepository.save(anyString(), anyString(), any()))
                .thenReturn(new EmailVerificationCode(1L, "alice", "HASH", Instant.now().plusSeconds(900), Instant.now(), false));

        UpdateProfileResult result = userService.updateUser(1L, "alice", "alice@new.com");

        assertThat(result.emailChangePending()).isTrue();
        assertThat(result.user().isEnabled()).isTrue();
        assertThat(result.user().getPendingEmail()).isEqualTo("alice@new.com");
        assertThat(result.user().getEmail()).isEqualTo("alice@old.com");
        verify(emailPort).sendVerificationCode(eq("alice@new.com"), eq("alice"), anyString());
        verify(refreshTokenPort, never()).revokeAll(any());
    }

    @Test
    void updateUser_emailChange_rejectsAlreadyUsedEmail() {
        User alice = User.fromPersisted(1L, "alice", "hashed", true, "alice@old.com", true, null, null, null, java.util.Set.of(), null, null);
        User bob   = User.fromPersisted(2L, "bob",   "hashed", true, "bob@email.com", true, null, null, null, java.util.Set.of(), null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("bob@email.com")).thenReturn(Optional.of(bob));

        assertThatThrownBy(() -> userService.updateUser(1L, "alice", "bob@email.com"))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    // ── email normalization ───────────────────────────────────────────────────

    @Test
    void registerUser_normalizesEmailToLowercase() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.empty());
        when(passwordHash.hash(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationCodeRepository.save(anyString(), anyString(), any()))
                .thenReturn(new EmailVerificationCode(1L, "alice", "CODE", Instant.now().plusSeconds(900), Instant.now(), false));

        User created = userService.registerUser("alice", "Password@123", "Alice@Test.COM", List.of());

        assertThat(created.getEmail()).isEqualTo("alice@test.com");
        verify(userRepository).findByEmail("alice@test.com");
        verify(emailPort).sendVerificationCode(eq("alice@test.com"), eq("alice"), anyString());
    }

    @Test
    void registerUser_rejectsDuplicateEmailRegardlessOfCase() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@test.com"))
                .thenReturn(Optional.of(User.of("other", "hashed", null)));

        assertThatThrownBy(() -> userService.registerUser("alice", "Password@123", "Alice@Test.COM", List.of()))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void requestPasswordReset_normalizesEmail() {
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.empty());

        userService.requestPasswordReset("Alice@Test.COM");

        verify(userRepository).findByEmail("alice@test.com");
        verifyNoInteractions(emailPort);
    }

    @Test
    void updateUser_normalizesNewEmailBeforeSave() {
        User user = User.fromPersisted(1L, "alice", "hashed", true, "alice@old.com", true, null, null, null, java.util.Set.of(), null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@new.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationCodeRepository.save(anyString(), anyString(), any()))
                .thenReturn(new EmailVerificationCode(1L, "alice", "CODE", Instant.now().plusSeconds(900), Instant.now(), false));

        UpdateProfileResult result = userService.updateUser(1L, "alice", "Alice@New.COM");

        assertThat(result.user().getPendingEmail()).isEqualTo("alice@new.com");
        verify(userRepository).findByEmail("alice@new.com");
        verify(emailPort).sendVerificationCode(eq("alice@new.com"), eq("alice"), anyString());
    }

    // ── PLAT-C054 — conta de cliente da loja não recebe nem perde roles pela gestão ─────────

    private static User customerAccount() {
        return User.customer("cli@x.com", "hashed", "cli@x.com", 7L, new java.util.HashSet<>());
    }

    @Test
    void assignRole_rejectsCustomerAccount() {
        when(userRepository.findByUsername("cli@x.com")).thenReturn(Optional.of(customerAccount()));

        assertThatThrownBy(() -> userService.assignRole("cli@x.com", "ROLE_ADMIN"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.user.CustomerAccountRolesImmutableException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void removeRole_rejectsCustomerAccount() {
        when(userRepository.findByUsername("cli@x.com")).thenReturn(Optional.of(customerAccount()));

        assertThatThrownBy(() -> userService.removeRole("cli@x.com", "ROLE_CUSTOMER"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.user.CustomerAccountRolesImmutableException.class);
        verify(userRepository, never()).save(any());
    }
}
