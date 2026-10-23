package com.cernecommerce.infra.security;

import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import com.cernecommerce.core.ports.out.ratelimit.ResourceRateLimiterPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.infra.handler.ApiError;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * Rate limit de endpoints de negócio sensíveis (PLAT-C030, extensão) — distinto de
 * {@link LoginRateLimitingFilter}, que só cobre autenticação e roda ANTES da autenticação JWT
 * (chave sempre IP). Este filtro precisa do usuário autenticado para duas das três rotas
 * protegidas, então é registrado DEPOIS de {@code JwtAuthenticationFilter} na cadeia
 * ({@code SecurityConfig}) — quando ele roda, {@code request.getUserPrincipal()} já reflete o
 * token validado, se houver.
 *
 * <p>Fica em {@code infra.security} (não injetado nos controllers) porque
 * {@code HexagonalArchitectureTest.adapter_in_controllers_must_not_depend_on_output_ports} proíbe
 * {@code adapter.in.controller} de depender de {@code core.ports.out} diretamente — o mesmo motivo
 * pelo qual {@link LoginRateLimitingFilter} já vive aqui, e não dentro de {@code AuthController}.</p>
 */
@Component
public class ResourceRateLimitingFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ResourceRateLimiterPort rateLimiter;
    private final Counter rateLimitBlockedCounter;

    public ResourceRateLimitingFilter(ResourceRateLimiterPort rateLimiter, MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.rateLimitBlockedCounter = meterRegistry.counter("resource.rate_limit.blocked.total");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        // Página do QR da mesa: pública, leitura e escrita, por IP.
        if (path.startsWith("/public/salao/")) return false;
        // ECM-F010 — cada checkout do app reserva estoque até o fim do dia e toca o aviso no caixa.
        if ("/shop/checkout".equals(path) && "POST".equalsIgnoreCase(method)) return false;
        if (!"GET".equalsIgnoreCase(method)) return true;

        return !"/crm/customers/export".equals(path)
                && !"/estoque/movements".equals(path)
                && !"/notifications/stream".equals(path)
                && !"/shop/catalog".equals(path)
                && !path.startsWith("/shop/catalog/")
                // ECM-F008 — o montador de kit é vitrine pública como o catálogo, e as opções de um
                // passo consultam saldo item a item: mesmo bucket, mesma chave por IP.
                && !"/shop/kits".equals(path)
                && !path.startsWith("/shop/kits/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String bucket = bucketFor(path, request.getMethod());
        String key = keyFor(bucket, request);

        try {
            rateLimiter.checkOrThrow(bucket, key);
        } catch (RateLimitExceededException ex) {
            rateLimitBlockedCounter.increment();
            ApiError error = ApiError.of(ex.getMessage(), "RATE_LIMIT_EXCEEDED", request.getRequestURI(),
                    MDC.get("traceId"));
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(ex.retryAfterSeconds()));
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

    private String bucketFor(String path, String method) {
        if ("/crm/customers/export".equals(path)) return "crm-export";
        if ("/estoque/movements".equals(path)) return "estoque-movements";
        if ("/notifications/stream".equals(path)) return "notifications-stream";
        if ("/shop/checkout".equals(path)) return "shop-checkout";
        if (path.startsWith("/public/salao/")) {
            // O código da mesa tem 4 dígitos: tentativas contadas à parte e bem apertadas.
            if (path.endsWith("/acesso")) return "salao-publico-acesso";
            return "GET".equalsIgnoreCase(method) ? "salao-publico-leitura" : "salao-publico-escrita";
        }
        return "shop-catalog";
    }

    /** Endpoints autenticados usam o usuário como chave; o catálogo (público) usa o IP. */
    private String keyFor(String bucket, HttpServletRequest request) {
        if ("shop-catalog".equals(bucket) || bucket.startsWith("salao-publico")) {
            return clientIp(request);
        }
        Principal principal = request.getUserPrincipal();
        return principal != null ? principal.getName() : clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        // Mesma premissa de LoginRateLimitingFilter.clientIp: server.forward-headers-strategy já
        // resolveu o IP real antes deste filtro rodar.
        return request.getRemoteAddr();
    }
}
