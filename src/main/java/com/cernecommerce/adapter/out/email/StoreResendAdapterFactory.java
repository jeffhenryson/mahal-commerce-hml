package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailSenderConfig;
import com.cernecommerce.core.ports.out.notification.DevAlertPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Monta o {@link ResendEmailAdapter} com as credenciais salvas em Dados da loja > Integrações.
 * Guarda o último montado: a configuração muda raramente e o {@link RestClient} é reaproveitável;
 * qualquer mudança de chave/remetente gera um adapter novo (o record compara todos os campos).
 */
@Component
class StoreResendAdapterFactory {

    private final String apiUrl;
    private final long ttlMinutes;
    private final String emailSubject;
    private final String verificationFrontendUrl;
    private final ThymeleafEmailRenderer renderer;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<DevAlertPort> devAlertPort;

    private volatile Cached cached;

    private record Cached(EmailSenderConfig config, ResendEmailAdapter adapter) { }

    StoreResendAdapterFactory(
            @Value("${resend.api-url:https://api.resend.com/emails}") String apiUrl,
            @Value("${email.verification.ttl-minutes:15}") long ttlMinutes,
            @Value("${email.verification.subject:Código de confirmação de cadastro}") String emailSubject,
            @Value("${email.verification.frontend-url:http://localhost:4200/auth/verify-email}") String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry,
            ObjectProvider<DevAlertPort> devAlertPort) {
        this.apiUrl = apiUrl;
        this.ttlMinutes = ttlMinutes;
        this.emailSubject = emailSubject;
        this.verificationFrontendUrl = verificationFrontendUrl;
        this.renderer = renderer;
        this.meterRegistry = meterRegistry;
        this.devAlertPort = devAlertPort;
    }

    ResendEmailAdapter adapterFor(EmailSenderConfig config) {
        Cached current = cached;
        if (current != null && current.config().equals(config)) {
            return current.adapter();
        }
        RestClient restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + config.apiKey())
                .build();
        ResendEmailAdapter adapter = new ResendEmailAdapter(restClient, config.formattedFrom(), config.replyTo(),
                ttlMinutes, emailSubject, verificationFrontendUrl, renderer, meterRegistry);
        adapter.reportFailuresTo(devAlertPort::getIfAvailable);
        cached = new Cached(config, adapter);
        return adapter;
    }
}
