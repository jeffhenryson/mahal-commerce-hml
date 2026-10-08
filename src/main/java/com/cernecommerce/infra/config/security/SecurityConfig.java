package com.cernecommerce.infra.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.cernecommerce.infra.security.MaintenanceModeFilter;
import com.cernecommerce.infra.security.RestAccessDeniedHandler;
import com.cernecommerce.infra.security.RestAuthenticationEntryPoint;
import com.cernecommerce.infra.security.TraceIdFilter;
import com.cernecommerce.infra.security.jwt.JwtAuthenticationFilter;
import com.cernecommerce.infra.security.LoginRateLimitingFilter;
import com.cernecommerce.infra.security.ResourceRateLimitingFilter;
import com.cernecommerce.infra.security.SseTicketAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${management.server.port:-1}")
    private int managementPort;

    @org.springframework.beans.factory.annotation.Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                           RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler deniedHandler,
                                           LoginRateLimitingFilter loginRateLimitingFilter,
                                           ResourceRateLimitingFilter resourceRateLimitingFilter,
                                           MaintenanceModeFilter maintenanceModeFilter,
                                           TraceIdFilter traceIdFilter,
                                           SseTicketAuthenticationFilter sseTicketAuthenticationFilter,
                                           @org.springframework.beans.factory.annotation.Value("${security.content-security-policy:}") String cspDirective,
                                           @org.springframework.beans.factory.annotation.Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerEnabled) throws Exception {
        // Convenção de autorização: sempre hasAuthority(), nunca hasRole().
        // Roles têm prefixo ROLE_ (ex: ROLE_ADMIN); permissões não (ex: USER_CREATE).
        // hasRole("ADMIN") adiciona o prefixo automaticamente e seria equivalente a
        // hasAuthority("ROLE_ADMIN"), mas misturar os dois métodos gera inconsistência.
        // Usar hasAuthority() para tudo é mais explícito e funciona para roles e permissões.
        http
            .csrf(csrf -> csrf.disable())
            // Headers de segurança: X-Content-Type-Options, X-Frame-Options e HSTS (HTTPS only)
            // vêm dos defaults do Spring Security. Adicionamos Referrer-Policy e CSP explicitamente.
            // CSP configurável via security.content-security-policy; vazio = desabilitado (dev/Swagger).
            .headers(headers -> {
                headers.referrerPolicy(r -> r.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                if (cspDirective != null && !cspDirective.isBlank()) {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(cspDirective));
                }
                // Permissions-Policy: restringe features do browser não usadas por uma API REST.
                headers.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"));
            })
            .authorizeHttpRequests(auth -> {
                // Onde o Swagger está habilitado (dev e hml), UI e spec são públicos — a
                // segurança real está em cada endpoint individual (@PreAuthorize); para fazer
                // chamadas, o usuário precisa clicar em Authorize e inserir o Bearer token.
                // Em prod, springdoc.*.enabled=false (PLAT-C029): este bloco não registra nada
                // e o spec não é servido. Gatear por role não funcionaria aqui — com
                // SessionCreationPolicy.STATELESS e httpBasic desabilitado, o navegador não tem
                // como enviar o Bearer token ao carregar a UI nem ao buscar o spec via fetch.
                if (swaggerEnabled) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                }
                // Actuator roda em management.server.port=8081 (hml/prod) — sem filtros desta
                // SecurityFilterChain. Em dev (mesma porta), as regras abaixo se aplicam.
                auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // ATENÇÃO: estas regras DEVEM vir antes de /auth/** permitAll abaixo.
                // GET e DELETE /auth/sessions exigem autenticação; a regra de /auth/** é mais ampla
                // e cobriria esses endpoints se declarada primeiro. Não reordene sem revisar.
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/auth/sessions").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/auth/sessions/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/auth/sessions").authenticated()
                .requestMatchers("/system/config/public").permitAll()
                .requestMatchers("/system/info").authenticated()
                .requestMatchers("/auth/verify-email", "/auth/resend-verification").permitAll()
                .requestMatchers("/auth/2fa/verify").permitAll()
                .requestMatchers("/auth/2fa/setup", "/auth/2fa/confirm", "/auth/2fa/replace").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/auth/2fa").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/auth/2fa/status").authenticated()
                .requestMatchers("/auth/2fa/backup-codes/regenerate").authenticated()
                // Etapa 1 DEV exige ROLE_DEV (via @PreAuthorize no controller)
                .requestMatchers("/auth/dev/first-code").authenticated()
                // Etapa 2 DEV é pública — devToken é a prova de identidade
                .requestMatchers("/auth/dev/complete").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/avatars/*").permitAll()
                // Imagem de produto é pública pelo mesmo motivo do avatar: a vitrine do
                // marketplace renderiza a foto sem token. O upload continua autenticado, em
                // POST /estoque/products/images sob ESTOQUE_PRODUCT_MANAGE.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/product-images/*").permitAll()
                // Fatia 8 (plano-pdv-marketplace.md §2.9): ramo /shop/** público começa aqui.
                // Cadastro e catálogo são públicos por natureza; carrinho/checkout (Fatia 9) exigem
                // SHOP_CART_OWN/SHOP_ORDER_OWN via @PreAuthorize, não regra de rota.
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/shop/register").permitAll()
                // ECM-F002: catálogo público — GET /shop/catalog e /shop/catalog/{sku}.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/shop/catalog", "/shop/catalog/*").permitAll()
                // Mesma natureza do catálogo: é a navegação da vitrine, lida sem login.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/shop/categories").permitAll()
                // ECM-F008: montador de kit da vitrine — mesma natureza do catálogo. Pôr o kit no
                // carrinho (/shop/cart/kits) continua exigindo SHOP_CART_OWN via @PreAuthorize.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/shop/kits", "/shop/kits/*",
                        "/shop/kits/*/steps/*/options").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/shop/kits/quote").permitAll()
                // ECM-F004 (Fatia 10): notificação do gateway de pagamento — é o próprio gateway
                // chamando, sem sessão de usuário nenhuma. A defesa mora dentro de
                // PaymentWebhookService (payment_check sempre reconsulta a verdade), não aqui.
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/webhooks/payments/*").permitAll()
                // WhatsApp Cloud API: a Meta verifica (GET, hub.verify_token) e notifica (POST,
                // assinado com X-Hub-Signature-256) — a defesa mora em WhatsappWebhookController.
                .requestMatchers("/webhooks/whatsapp").permitAll();
                // Em hml/prod management.server.port != server.port: actuator só existe na porta
                // de management (8081) e é protegido por rede — sem auth JWT necessária.
                // Em dev (mesma porta): exige DEV_ELEVATED para não expor métricas publicamente.
                if (managementPort > 0 && managementPort != serverPort) {
                    auth.requestMatchers("/actuator/**").permitAll();
                } else {
                    auth.requestMatchers("/actuator/**").hasAuthority("DEV_ELEVATED");
                }
                auth.anyRequest().authenticated();
            })
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
            .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(maintenanceModeFilter, TraceIdFilter.class)
            .addFilterBefore(loginRateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // PLAT-C051 — depois do JWT, e só age se o contexto ainda estiver vazio: quem consegue
            // mandar o header Authorization continua autenticando pelo caminho normal. O bilhete é
            // a saída para o EventSource do navegador, que não envia headers, e vale para uma rota
            // só (GET /notifications/stream), um uso e alguns segundos.
            .addFilterAfter(sseTicketAuthenticationFilter, JwtAuthenticationFilter.class)
            // Depois da autenticação, não antes: crm-export/estoque-movements/notifications-stream
            // usam o usuário autenticado como chave (request.getUserPrincipal()). Encadeado no
            // filtro de bilhete, e não no de JWT, porque o stream se autentica naquele — a ordem
            // entre os dois deixa de depender de qual foi registrado primeiro.
            .addFilterAfter(resourceRateLimitingFilter, SseTicketAuthenticationFilter.class)
            .cors(Customizer.withDefaults());
        return http.build();
    }

    // ProviderManager.eraseCredentialsAfterAuthentication is disabled because the default behaviour
    // calls UserDetails.eraseCredentials() which nullifies the password hash on the object stored
    // in the @Cacheable cache, causing BadCredentialsException on subsequent login attempts.
    // Disabling erasure retains the BCrypt hash in memory (the plaintext is never cached here);
    // the raw password in the Authentication token is still garbage-collected after the request.
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder,
                                                       org.springframework.security.authentication.AuthenticationEventPublisher authEventPublisher) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        ProviderManager manager = new ProviderManager(provider);
        manager.setEraseCredentialsAfterAuthentication(false);
        // Wire the event publisher so ProviderManager fires AbstractAuthenticationFailureEvent
        // (default NullEventPublisher silently discards auth events when manager is created manually).
        manager.setAuthenticationEventPublisher(authEventPublisher);
        return manager;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:*}") String allowedOrigins,
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}") String allowedMethods,
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-headers:*}") String allowedHeaders,
            @org.springframework.beans.factory.annotation.Value("${cors.exposed-headers:X-Trace-Id}") String exposedHeaders,
            @org.springframework.beans.factory.annotation.Value("${cors.allow-credentials:false}") boolean allowCredentials) {
        CorsConfiguration config = new CorsConfiguration();
        // allowCredentials=true é incompatível com allowedOrigins("*") pela spec CORS.
        // Quando cookies HttpOnly estão ativos (hml/prod), origens devem ser explícitas.
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                "cors.allow-credentials=true requer origens explícitas em cors.allowed-origins (não pode usar '*')");
        }
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of(allowedMethods.split(",")));
        config.setAllowedHeaders(List.of(allowedHeaders.split(",")));
        config.setExposedHeaders(List.of(exposedHeaders.split(",")));
        config.setAllowCredentials(allowCredentials);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}