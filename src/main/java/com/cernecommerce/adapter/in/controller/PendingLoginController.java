package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.PasswordChangeRequiredRequest;
import com.cernecommerce.adapter.in.dtos.request.TotpEnrollmentConfirmRequest;
import com.cernecommerce.adapter.in.dtos.request.TotpEnrollmentSetupRequest;
import com.cernecommerce.adapter.in.dtos.response.TokenPairResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TotpEnrollmentConfirmResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TotpEnrollmentSetupResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.in.PendingLoginUseCase;
import com.cernecommerce.core.ports.in.TotpUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pendências do login: senha temporária e 2FA exigido. Públicas: quem autoriza é o token de pendência,
 * que só serve aqui. Token inválido responde 400, não 401: o cliente volta ao login sem tentar renovar.
 */
@RestController
@RequestMapping("/auth")
public class PendingLoginController {

    private static final String REFRESH_COOKIE = "refreshToken";

    private final PendingLoginUseCase pendingLogin;
    private final AuthUseCase authUseCase;
    private final ApplicationEventPublisher publisher;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final boolean cookieSecure;

    public PendingLoginController(PendingLoginUseCase pendingLogin, AuthUseCase authUseCase,
            ApplicationEventPublisher publisher,
            @Value("${jwt.access-ttl-minutes:15}") long accessTtlMinutes,
            @Value("${jwt.refresh-ttl-days:7}") long refreshTtlDays,
            @Value("${cookie.secure:true}") boolean cookieSecure) {
        this.pendingLogin = pendingLogin;
        this.authUseCase = authUseCase;
        this.publisher = publisher;
        this.accessTtlSeconds = accessTtlMinutes * 60;
        this.refreshTtlSeconds = refreshTtlDays * 86400;
        this.cookieSecure = cookieSecure;
    }

    @Operation(summary = "Troca a senha temporária e continua o login",
            description = "Devolve tokens, ou enrollmentToken se o 2FA também estiver pendente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens ou pendência de 2FA", content = @Content(
                    schema = @Schema(implementation = TokenPairResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "PENDING_LOGIN_TOKEN_INVALID ou INVALID_PASSWORD",
                    content = @Content)
    })
    @PostMapping("/password-change-required")
    public ResponseEntity<?> changeRequiredPassword(@Valid @RequestBody PasswordChangeRequiredRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        LoginResponse login = pendingLogin.changeRequiredPassword(request.passwordChangeToken(), request.newPassword());
        Object pending = AuthUtils.pendingLoginBody(login);
        if (pending != null) {
            return ResponseEntity.ok(pending);
        }
        return ResponseEntity.ok(finish(login.tokenPair(), httpRequest, response));
    }

    @Operation(summary = "Gera o segredo para cadastrar o 2FA exigido")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Segredo e URI do QR"),
            @ApiResponse(responseCode = "400", description = "PENDING_LOGIN_TOKEN_INVALID", content = @Content)
    })
    @PostMapping("/2fa/enroll/setup")
    public TotpEnrollmentSetupResponseDTO startEnrollment(@Valid @RequestBody TotpEnrollmentSetupRequest request) {
        TotpUseCase.TotpSetupResult setup = pendingLogin.startEnrollment(request.enrollmentToken());
        return new TotpEnrollmentSetupResponseDTO(setup.secret(), setup.otpauthUri());
    }

    @Operation(summary = "Confirma o 2FA exigido e conclui o login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Códigos de recuperação e tokens"),
            @ApiResponse(responseCode = "400", description = "PENDING_LOGIN_TOKEN_INVALID ou código inválido",
                    content = @Content)
    })
    @PostMapping("/2fa/enroll/confirm")
    public TotpEnrollmentConfirmResponseDTO confirmEnrollment(@Valid @RequestBody TotpEnrollmentConfirmRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        PendingLoginUseCase.EnrollmentResult result =
                pendingLogin.confirmEnrollment(request.enrollmentToken(), request.code());
        return new TotpEnrollmentConfirmResponseDTO(result.backupCodes(),
                finish(result.tokens(), httpRequest, response));
    }

    private TokenPairResponseDTO finish(TokenPair pair, HttpServletRequest request, HttpServletResponse response) {
        authUseCase.recordLogin(pair.getUsername(), request.getRemoteAddr());
        publisher.publishEvent(AuditEvent.of(EventType.USER_LOGGED_IN, pair.getUsername()));
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, pair.getRefreshToken())
                .httpOnly(true).path("/auth").maxAge(refreshTtlSeconds).sameSite("Strict").secure(cookieSecure)
                .build().toString());
        return new TokenPairResponseDTO(pair.getAccessToken(), pair.getRefreshToken(), accessTtlSeconds);
    }
}
