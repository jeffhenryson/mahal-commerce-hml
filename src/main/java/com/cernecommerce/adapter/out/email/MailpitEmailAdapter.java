package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public class MailpitEmailAdapter extends TemplatedEmailAdapter {

    private static final Logger log = LoggerFactory.getLogger(MailpitEmailAdapter.class);
    private final RestClient restClient;
    private final String fromAddress;

    public MailpitEmailAdapter(
            RestClient restClient,
            String fromAddress,
            long ttlMinutes,
            String emailSubject,
            String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry) {
        super(ttlMinutes, emailSubject, verificationFrontendUrl, renderer, meterRegistry);
        this.restClient = restClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public EmailChannelStatus channelStatus() {
        return EmailChannelStatus.of(true, "MAILPIT", "Conectado ao Mailpit (ambiente de homologação)");
    }

    @Override
    protected void deliver(String to, String subject, String html, String logPrefix) {
        Map<String, Object> body = Map.of(
                "From", Map.of("Email", fromAddress),
                "To", List.of(Map.of("Email", to)),
                "Subject", subject,
                "HTML", html
        );
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("{}.sent to={} via Mailpit", logPrefix, to);
            meterRegistry.counter("email.sent.total", "type", logPrefix).increment();
        } catch (Exception ex) {
            log.error("{}.failed to={} via Mailpit error={}", logPrefix, to, ex.getMessage());
            meterRegistry.counter("email.failed.total", "type", logPrefix).increment();
            reportFailure(logPrefix, to, ex.getMessage());
            throw new EmailDeliveryException(ex.getMessage());
        }
    }
}
