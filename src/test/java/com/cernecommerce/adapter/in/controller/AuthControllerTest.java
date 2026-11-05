package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.core.domain.model.auth.PasswordResetResult;
import com.cernecommerce.adapter.in.dtos.request.LoginRequest;
import com.cernecommerce.adapter.in.dtos.request.LogoutRequest;
import com.cernecommerce.adapter.in.dtos.request.RefreshRequest;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.auth.AccountLockedException;
import com.cernecommerce.core.domain.exception.auth.PasswordResetTokenExpiredException;
import com.cernecommerce.core.domain.exception.auth.PasswordResetTokenNotFoundException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.SessionInfo;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

public class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthUseCase authUseCase;
    private UserUseCase userUseCase;
    private ApplicationEventPublisher publisher;
    private SystemConfigUseCase systemConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @BeforeEach
    void setup() {
        authUseCase = mock(AuthUseCase.class);
        userUseCase = mock(UserUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        systemConfig = mock(SystemConfigUseCase.class);
        when(systemConfig.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        AuthController controller = new AuthController(authUseCase, userUseCase, publisher, systemConfig, 15, 7L, false);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.cernecommerce.infra.handler.GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_returns_access_and_refresh() throws Exception {
        var req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin@dev1");

        when(authUseCase.login("admin", "Admin@dev1"))
                .thenReturn(LoginResponse.success(new TokenPair("access123", "refresh123")));

        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_with_bad_credentials_returns_401() throws Exception {
        var req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        when(authUseCase.login(any(), any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void login_withAccountLockedException_publishesAuditEvent() throws Exception {
        var req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        when(authUseCase.login("admin", "wrong"))
                .thenThrow(new AccountLockedException("admin"));

        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests());

        verify(publisher).publishEvent((Object) argThat(e ->
                e instanceof AuditEvent ae &&
                ae.type() == AuditEvent.EventType.ACCOUNT_LOCKED));
    }

    @Test
    void refresh_with_invalid_token_returns_400() throws Exception {
        var req = new RefreshRequest();
        req.setRefreshToken("invalid");

        when(authUseCase.refresh("invalid"))
                .thenThrow(new IllegalArgumentException("Invalid refresh token"));

        mockMvc.perform(post("/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_rotates_and_returns_new_pair() throws Exception {
        var req = new RefreshRequest();
        req.setRefreshToken("oldRefresh");

        when(authUseCase.refresh("oldRefresh"))
                .thenReturn(new TokenPair("newAccess", "newRefresh"));

        mockMvc.perform(post("/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccess"))
                .andExpect(jsonPath("$.refreshToken").value("newRefresh"));
    }

    @Test
    void refresh_withRefreshTokenAlreadyUsedException_publishesAuditEvent() throws Exception {
        var req = new RefreshRequest();
        req.setRefreshToken("stolenRefresh");

        when(authUseCase.refresh("stolenRefresh"))
                .thenThrow(new RefreshTokenAlreadyUsedException("alice"));

        mockMvc.perform(post("/auth/refresh")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(publisher).publishEvent((Object) argThat(e ->
                e instanceof AuditEvent ae &&
                ae.type() == AuditEvent.EventType.TOKEN_THEFT_DETECTED));
    }

    @Test
    void logout_revokes_refresh() throws Exception {
        var req = new LogoutRequest();
        req.setRefreshToken("ref");

        mockMvc.perform(post("/auth/logout")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(authUseCase, times(1)).logout("ref");
    }

    @Test
    void login_when_2fa_enabled_returns_pending_challenge() throws Exception {
        when(authUseCase.login("admin", "Admin@dev1"))
                .thenReturn(LoginResponse.twoFactorChallenge("challenge-token-xyz"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"Admin@dev1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_2FA"))
                .andExpect(jsonPath("$.challengeToken").value("challenge-token-xyz"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void logout_without_token_returns_204_silently() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());

        verify(authUseCase, never()).logout(any());
    }

    @Test
    void refresh_without_body_or_cookie_returns_401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(authUseCase, never()).refresh(any());
    }

    // ── sessions ──────────────────────────────────────────────────────────────

    @Test
    void list_sessions_returns_200_with_sessions() throws Exception {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(86400);
        when(authUseCase.listActiveSessions("alice"))
                .thenReturn(List.of(new SessionInfo(1L, now, exp, "127.0.0.1", "Mozilla/5.0")));

        mockMvc.perform(get("/auth/sessions").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"));
    }

    @Test
    void logout_all_sessions_returns_204() throws Exception {
        mockMvc.perform(delete("/auth/sessions").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(authUseCase).logoutAll("alice");
    }

    @Test
    void revoke_specific_session_returns_204() throws Exception {
        mockMvc.perform(delete("/auth/sessions/42").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(authUseCase).revokeSession(42L, "alice");
    }

    // ── forgot / reset password ───────────────────────────────────────────────

    @Test
    void forgot_password_always_returns_204() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isNoContent());

        verify(userUseCase).requestPasswordReset("alice@example.com");
    }

    @Test
    void forgot_password_returns_503_when_disabled() throws Exception {
        when(systemConfig.getBoolean("auth.forgot-password.enabled", true)).thenReturn(false);

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(userUseCase);
    }

    @Test
    void reset_password_returns_204_on_success() throws Exception {
        when(userUseCase.resetPassword("validtoken", "NewPass@1")).thenReturn(new PasswordResetResult("alice", false));

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"validtoken\",\"newPassword\":\"NewPass@1\"}"))
                .andExpect(status().isNoContent());

        verify(userUseCase).resetPassword("validtoken", "NewPass@1");
    }

    @Test
    void reset_password_com_convite_publica_evento_proprio() throws Exception {
        when(userUseCase.resetPassword("validtoken", "NewPass@1")).thenReturn(new PasswordResetResult("alice", true));

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"validtoken\",\"newPassword\":\"NewPass@1\"}"))
                .andExpect(status().isNoContent());

        verify(publisher).publishEvent((Object) argThat(e -> e instanceof AuditEvent event
                && event.type() == AuditEvent.EventType.USER_INVITE_ACCEPTED));
    }

    @Test
    void reset_password_with_expired_token_returns_400() throws Exception {
        when(userUseCase.resetPassword(any(), any()))
                .thenThrow(new PasswordResetTokenExpiredException());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expiredtoken\",\"newPassword\":\"NewPass@1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reset_password_with_invalid_token_returns_400() throws Exception {
        when(userUseCase.resetPassword(any(), any()))
                .thenThrow(new PasswordResetTokenNotFoundException());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"unknowntoken\",\"newPassword\":\"NewPass@1\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── confirm email change ──────────────────────────────────────────────────

    @Test
    void confirm_email_change_returns_204_on_success() throws Exception {
        when(userUseCase.confirmEmailChange("ABCDEF123456")).thenReturn("alice");

        mockMvc.perform(post("/auth/confirm-email-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCDEF123456\"}"))
                .andExpect(status().isNoContent());

        verify(userUseCase).confirmEmailChange("ABCDEF123456");
    }
}
