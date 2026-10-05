package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.GoogleLoginRequest;
import com.cernecommerce.adapter.in.dtos.response.TokenPairResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.auth.OAuthLoginResult;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.ports.in.OAuthLoginUseCase;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@RestController
@RequestMapping("/auth/oauth2")
public class OAuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final ApplicationEventPublisher publisher;
    private final SystemConfigUseCase systemConfig;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final boolean cookieSecure;

    public OAuthController(OAuthLoginUseCase oAuthLoginUseCase,
                           ApplicationEventPublisher publisher,
                           SystemConfigUseCase systemConfig,
                           @Value("${jwt.access-ttl-minutes:15}") long accessTtlMinutes,
                           @Value("${jwt.refresh-ttl-days:7}") long refreshTtlDays,
                           @Value("${cookie.secure:true}") boolean cookieSecure) {
        this.oAuthLoginUseCase = oAuthLoginUseCase;
        this.publisher = publisher;
        this.systemConfig = systemConfig;
        this.accessTtlSeconds = accessTtlMinutes * 60;
        this.refreshTtlSeconds = refreshTtlDays * 86400;
        this.cookieSecure = cookieSecure;
    }

    @Operation(summary = "Login com Google — valida o ID token do Google e retorna tokens JWT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login realizado",
                    content = @Content(schema = @Schema(implementation = TokenPairResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Token Google inválido ou expirado", content = @Content)
    })
    @PostMapping("/google")
    ResponseEntity<TokenPairResponseDTO> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletResponse response) {
        if (!systemConfig.getBoolean("auth.google.enabled", true)) {
            publisher.publishEvent(AuditEvent.of(EventType.OAUTH_GOOGLE_DISABLED_ATTEMPT, "anonymous"));
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        OAuthLoginResult result = oAuthLoginUseCase.loginWithGoogle(request.idToken());
        TokenPair pair = result.tokenPair();
        publisher.publishEvent(AuditEvent.of(EventType.OAUTH_GOOGLE_LOGIN, result.username()));
        if (result.firstGoogleLogin()) {
            publisher.publishEvent(AuditEvent.of(EventType.OAUTH_GOOGLE_LINKED, result.username()));
        }
        response.addHeader("Set-Cookie", buildCookie(pair.getRefreshToken(), refreshTtlSeconds).toString());
        return ResponseEntity.ok(new TokenPairResponseDTO(pair.getAccessToken(), pair.getRefreshToken(), accessTtlSeconds));
    }

    private ResponseCookie buildCookie(String value, long maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .path("/auth")
                .maxAge(maxAge)
                .sameSite("Strict")
                .secure(cookieSecure)
                .build();
    }
}
