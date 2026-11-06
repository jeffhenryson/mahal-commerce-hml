package com.cernecommerce.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.cernecommerce.infra.handler.ApiError;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    // Static mapper avoids a Spring-managed ObjectMapper dependency that can be absent
    // in lightweight test contexts (e.g. @SpringBootTest without full Jackson auto-config).
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final LoginRateLimiterPort rateLimiter;
    private final long windowSeconds;
    private final Counter rateLimitBlockedCounter;

    public LoginRateLimitingFilter(LoginRateLimiterPort rateLimiter,
                                   MeterRegistry meterRegistry,
                                   @Value("${rate.limit.login.window-seconds:60}") long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.windowSeconds = windowSeconds;
        this.rateLimitBlockedCounter = meterRegistry.counter("auth.rate_limit.blocked.total");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Subtract context-path so the check works with or without server.servlet.context-path.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();

        // DELETE /auth/2fa accepts password + TOTP code — protect against brute-force.
        if ("DELETE".equalsIgnoreCase(method)) {
            return !"/auth/2fa".equals(path);
        }

        // PUT /notifications/preferences/{type} accepts user input — guard against spam.
        if ("PUT".equalsIgnoreCase(method)) {
            return !path.startsWith("/notifications/preferences/");
        }

        // PLAT-C051 — GET /notifications/stream saiu daqui. O limite deste filtro é por IP, e o
        // stream é o único endpoint autenticado que ele guardava: num salão atrás de um NAT só,
        // um cliente em laço de reconexão consumia o balde de todo mundo e derrubava a loja
        // inteira. Agora ele é guardado pelo ResourceRateLimitingFilter, que roda depois da
        // autenticação e usa o usuário como chave — que é o que se quer limitar aqui.
        if (!"POST".equalsIgnoreCase(method)) return true;
        return !"/auth/login".equals(path)
                && !"/auth/register".equals(path)
                && !"/auth/verify-email".equals(path)
                && !"/auth/confirm-email-change".equals(path)
                && !"/auth/resend-verification".equals(path)
                && !"/auth/refresh".equals(path)
                && !"/auth/forgot-password".equals(path)
                && !"/auth/reset-password".equals(path)
                && !"/auth/2fa/verify".equals(path)
                && !"/auth/password-change-required".equals(path)
                && !"/auth/2fa/enroll/setup".equals(path)
                && !"/auth/2fa/enroll/confirm".equals(path)
                && !"/auth/2fa/confirm".equals(path)
                && !"/auth/2fa/replace".equals(path)
                && !"/auth/2fa/backup-codes/regenerate".equals(path)
                && !"/auth/oauth2/google".equals(path)
                && !"/auth/dev/first-code".equals(path)
                && !"/auth/dev/complete".equals(path)
                // PLAT-C030 / Fatia 8: /shop/register cria linhas sem autenticação — mesmo risco
                // de abuso que /auth/register, mesmo limite.
                && !"/shop/register".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimiter.tryConsume(clientIp(request))) {
            rateLimitBlockedCounter.increment();
            ApiError error = ApiError.of(
                    "Muitas tentativas — aguarde antes de tentar novamente",
                    "TOO_MANY_REQUESTS",
                    request.getRequestURI(),
                    MDC.get("traceId"));
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            // PLAT-C050 — o charset é obrigatório aqui: MediaType.APPLICATION_JSON_VALUE é
            // "application/json" seco, e sem ele o getWriter() do Tomcat cai no default do
            // container. A mensagem acentuada saía mangled ("Muitas tentativas ? aguarde"), e o
            // cliente ainda recebia um Content-Type sem charset. Mesmo par setContentType +
            // setCharacterEncoding de RestAuthenticationEntryPoint e RestAccessDeniedHandler.
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            MAPPER.writeValue(response.getWriter(), error);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        // Rely on server.forward-headers-strategy (native in hml/prod, none in dev).
        // Spring/Tomcat already resolves the real client IP before this filter runs.
        return request.getRemoteAddr();
    }
}
