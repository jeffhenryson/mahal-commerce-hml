package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
import com.cernecommerce.core.ports.out.notification.DevAlertPort;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;

/**
 * O {@link EmailPort} injetado é o {@link RoutingEmailAdapter}: usa o Resend configurado em Dados
 * da loja &gt; Integrações quando ativo e, fora isso, o adapter de ambiente ({@code fallbackEmailPort})
 * escolhido por {@code email.provider}:
 * <ul>
 *   <li>{@code resend} — envia via API Resend (requer {@code resend.api-key} real).</li>
 *   <li>{@code mailpit} — envia para o Mailpit (hml).</li>
 *   <li>Qualquer outro valor / ausente — loga no console (padrão dev/testes).</li>
 * </ul>
 */
@Configuration
class EmailAdapterConfig {

    static final String FALLBACK = "fallbackEmailPort";

    @Bean
    @Primary
    RoutingEmailAdapter routingEmailAdapter(@Qualifier(FALLBACK) EmailPort fallback,
            EmailIntegrationUseCase emailIntegrationUseCase, StoreResendAdapterFactory storeResendAdapterFactory,
            @Qualifier("emailTaskExecutor") Executor emailTaskExecutor) {
        return new RoutingEmailAdapter(fallback, emailIntegrationUseCase, storeResendAdapterFactory, emailTaskExecutor);
    }

    @Bean
    @Qualifier(FALLBACK)
    @ConditionalOnProperty(name = "email.provider", havingValue = "resend")
    ResendEmailAdapter resendEmailAdapter(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from:noreply@example.com}") String fromAddress,
            @Value("${resend.api-url:https://api.resend.com/emails}") String apiUrl,
            @Value("${email.verification.ttl-minutes:15}") long ttlMinutes,
            @Value("${email.verification.subject:Código de confirmação de cadastro}") String emailSubject,
            @Value("${email.verification.frontend-url:http://localhost:4200/auth/verify-email}") String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry,
            ObjectProvider<DevAlertPort> devAlertPort) {
        RestClient restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        ResendEmailAdapter adapter = new ResendEmailAdapter(restClient, fromAddress, ttlMinutes, emailSubject,
                verificationFrontendUrl, renderer, meterRegistry);
        adapter.reportFailuresTo(devAlertPort::getIfAvailable);
        return adapter;
    }

    @Bean
    @Qualifier(FALLBACK)
    @ConditionalOnProperty(name = "email.provider", havingValue = "mailpit")
    MailpitEmailAdapter mailpitEmailAdapter(
            @Value("${mailpit.from:noreply@cernedsgn.xyz}") String fromAddress,
            @Value("${mailpit.api-url:http://mailpit-mahal:8025/api/v1/send}") String apiUrl,
            @Value("${email.verification.ttl-minutes:15}") long ttlMinutes,
            @Value("${email.verification.subject:Código de confirmação de cadastro}") String emailSubject,
            @Value("${email.verification.frontend-url:http://localhost:4201/auth/verify-email}") String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry,
            ObjectProvider<DevAlertPort> devAlertPort) {
        RestClient restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
        MailpitEmailAdapter adapter = new MailpitEmailAdapter(restClient, fromAddress, ttlMinutes, emailSubject,
                verificationFrontendUrl, renderer, meterRegistry);
        adapter.reportFailuresTo(devAlertPort::getIfAvailable);
        return adapter;
    }

    // Condição explícita (e não @ConditionalOnMissingBean(EmailPort.class)): o RoutingEmailAdapter
    // também é um EmailPort e faria o logging sumir.
    @Bean
    @Qualifier(FALLBACK)
    @ConditionalOnExpression("!'${email.provider:logging}'.equals('resend') && !'${email.provider:logging}'.equals('mailpit')")
    LoggingEmailAdapter loggingEmailAdapter() {
        return new LoggingEmailAdapter();
    }
}
