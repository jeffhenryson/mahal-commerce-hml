package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.auth.PasswordResetResult;
import com.cernecommerce.adapter.in.dtos.request.ForgotPasswordRequest;
import com.cernecommerce.adapter.in.dtos.request.LoginRequest;
import com.cernecommerce.adapter.in.dtos.request.LogoutRequest;
import com.cernecommerce.adapter.in.dtos.request.RefreshRequest;
import com.cernecommerce.adapter.in.dtos.request.ResetPasswordRequest;
import com.cernecommerce.adapter.in.dtos.request.TotpVerifyRequest;
import com.cernecommerce.adapter.in.dtos.request.VerifyEmailRequest;
import com.cernecommerce.adapter.in.dtos.response.SessionInfoDTO;
import com.cernecommerce.adapter.in.dtos.response.TokenPairResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TwoFactorChallengeResponseDTO;
import com.cernecommerce.core.domain.exception.auth.AccountLockedException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthUseCase authUseCase;
    private final UserUseCase userUseCase;
    private final ApplicationEventPublisher publisher;
    private final SystemConfigUseCase systemConfig;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final boolean cookieSecure;

    public AuthController(AuthUseCase authUseCase,
            UserUseCase userUseCase,
            ApplicationEventPublisher publisher,
            SystemConfigUseCase systemConfig,
            @Value("${jwt.access-ttl-minutes:15}") long accessTtlMinutes,
            @Value("${jwt.refresh-ttl-days:7}") long refreshTtlDays,
            @Value("${cookie.secure:true}") boolean cookieSecure) {
        this.authUseCase = authUseCase;
        this.userUseCase = userUseCase;
        this.publisher = publisher;
        this.systemConfig = systemConfig;
        this.accessTtlSeconds = accessTtlMinutes * 60;
        this.refreshTtlSeconds = refreshTtlDays * 86400;
        this.cookieSecure = cookieSecure;
    }

    @Operation(summary = "Login — retorna tokens ou challenge de 2FA")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login completo (TokenPairResponseDTO) ou 2FA pendente (TwoFactorChallengeResponseDTO)"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas", content = @Content(schema = @Schema(implementation = com.cernecommerce.infra.handler.ApiError.class)))
    })
    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            LoginResponse loginResponse = authUseCase.login(request.getUsername(), request.getPassword());
            if (loginResponse.twoFactorRequired()) {
                return ResponseEntity.ok(new TwoFactorChallengeResponseDTO("PENDING_2FA", loginResponse.challengeToken(), 300));
            }
            TokenPair pair = loginResponse.tokenPair();
            publisher.publishEvent(AuditEvent.of(EventType.USER_LOGGED_IN, request.getUsername()));
            setRefreshCookie(response, pair.getRefreshToken());
            // refreshToken também no body para retrocompatibilidade com clientes que ainda lêem o JSON
            return ResponseEntity.ok(new TokenPairResponseDTO(pair.getAccessToken(), pair.getRefreshToken(), accessTtlSeconds));
        } catch (AccountLockedException ex) {
            publisher.publishEvent(AuditEvent.of(EventType.ACCOUNT_LOCKED, request.getUsername()));
            throw ex;
        }
    }

    @Operation(summary = "Completa o login 2FA com código TOTP ou backup code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login completo",
                    content = @Content(schema = @Schema(implementation = TokenPairResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Código inválido ou challenge expirado", content = @Content)
    })
    @PostMapping("/2fa/verify")
    ResponseEntity<TokenPairResponseDTO> verifyTwoFactor(@Valid @RequestBody TotpVerifyRequest request,
            HttpServletResponse response) {
        TokenPair pair = authUseCase.completeTwoFactorLogin(request.getChallengeToken(), request.getCode());
        publisher.publishEvent(AuditEvent.of(EventType.USER_LOGGED_IN, pair.getUsername()));
        setRefreshCookie(response, pair.getRefreshToken());
        return ResponseEntity.ok(new TokenPairResponseDTO(pair.getAccessToken(), pair.getRefreshToken(), accessTtlSeconds));
    }

    @Operation(summary = "Rotaciona refresh e emite novo access + refresh — aceita cookie ou body")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens emitidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPairResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Refresh inválido/expirado", content = @Content)
    })
    @PostMapping("/refresh")
    ResponseEntity<TokenPairResponseDTO> refresh(
            @RequestBody(required = false) RefreshRequest body,
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
            HttpServletResponse response) {
        String token = cookieToken != null ? cookieToken
                : (body != null ? body.getRefreshToken() : null);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            TokenPair pair = authUseCase.refresh(token);
            setRefreshCookie(response, pair.getRefreshToken());
            return ResponseEntity.ok(new TokenPairResponseDTO(pair.getAccessToken(), pair.getRefreshToken(), accessTtlSeconds));
        } catch (RefreshTokenAlreadyUsedException ex) {
            publisher.publishEvent(AuditEvent.of(EventType.TOKEN_THEFT_DETECTED, ex.getUsername()));
            throw ex;
        }
    }

    @Operation(summary = "Revoga o refresh token (logout) — aceita cookie ou body")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Revogado"),
            @ApiResponse(responseCode = "401", description = "Refresh inválido", content = @Content)
    })
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest body,
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
            HttpServletResponse response,
            Authentication authentication) {
        String token = cookieToken != null ? cookieToken
                : (body != null ? body.getRefreshToken() : null);
        if (token != null) authUseCase.logout(token);
        clearRefreshCookie(response);
        if (authentication != null) {
            publisher.publishEvent(AuditEvent.of(EventType.USER_LOGGED_OUT, authentication.getName()));
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoga todos os refresh tokens do usuário autenticado (logout total)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Todos os tokens revogados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @DeleteMapping("/sessions")
    ResponseEntity<Void> logoutAll(Authentication authentication, HttpServletResponse response) {
        authUseCase.logoutAll(authentication.getName());
        clearRefreshCookie(response);
        publisher.publishEvent(AuditEvent.of(EventType.USER_SESSIONS_CLEARED, authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lista as sessões ativas do usuário autenticado")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de sessões"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping("/sessions")
    ResponseEntity<List<SessionInfoDTO>> listSessions(Authentication authentication) {
        List<SessionInfoDTO> sessions = authUseCase.listActiveSessions(authentication.getName())
                .stream().map(SessionInfoDTO::from).toList();
        return ResponseEntity.ok(sessions);
    }

    @Operation(summary = "Revoga uma sessão específica do usuário autenticado")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Sessão revogada"),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> revokeSession(@PathVariable Long id, Authentication authentication) {
        authUseCase.revokeSession(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Inicia o fluxo de recuperação de senha (sempre retorna 204)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Email enviado se o endereço existir")
    })
    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        if (!systemConfig.getBoolean("auth.forgot-password.enabled", true)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        userUseCase.requestPasswordReset(request.getEmail());
        publisher.publishEvent(AuditEvent.of(EventType.PASSWORD_RESET_REQUESTED, request.getEmail()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Redefine a senha usando o token recebido por email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Senha redefinida"),
            @ApiResponse(responseCode = "400", description = "Token inválido, expirado ou senha fraca",
                    content = @Content(schema = @Schema(implementation = com.cernecommerce.infra.handler.ApiError.class)))
    })
    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        PasswordResetResult result = userUseCase.resetPassword(request.getToken(), request.getNewPassword());
        publisher.publishEvent(AuditEvent.of(
                result.invite() ? EventType.USER_INVITE_ACCEPTED : EventType.PASSWORD_RESET_COMPLETED, result.username()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Confirma a troca de email usando o código enviado ao novo endereço")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Email alterado"),
            @ApiResponse(responseCode = "400", description = "Código inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = com.cernecommerce.infra.handler.ApiError.class)))
    })
    @PostMapping("/confirm-email-change")
    ResponseEntity<Void> confirmEmailChange(@Valid @RequestBody VerifyEmailRequest request) {
        String username = userUseCase.confirmEmailChange(request.getCode());
        publisher.publishEvent(AuditEvent.of(EventType.EMAIL_CHANGE_CONFIRMED, username));
        return ResponseEntity.noContent().build();
    }

    // --- helpers de cookie ---

    private void setRefreshCookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", buildCookie(token, refreshTtlSeconds).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie("", 0).toString());
    }

    private ResponseCookie buildCookie(String value, long maxAge) {
        // Path=/auth restringe o envio do cookie apenas aos endpoints de auth,
        // evitando que o refreshToken apareça nos logs de acesso de outros endpoints.
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .path("/auth")
                .maxAge(maxAge)
                .sameSite("Strict")
                .secure(cookieSecure)
                .build();
    }
}
