package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResendEmailAdapter extends TemplatedEmailAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailAdapter.class);
    private final RestClient restClient;
    private final String fromAddress;
    private final String replyTo;

    public ResendEmailAdapter(
            RestClient restClient,
            String fromAddress,
            long ttlMinutes,
            String emailSubject,
            String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry) {
        this(restClient, fromAddress, null, ttlMinutes, emailSubject, verificationFrontendUrl, renderer, meterRegistry);
    }

    public ResendEmailAdapter(
            RestClient restClient,
            String fromAddress,
            String replyTo,
            long ttlMinutes,
            String emailSubject,
            String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer,
            MeterRegistry meterRegistry) {
        super(ttlMinutes, emailSubject, verificationFrontendUrl, renderer, meterRegistry);
        this.restClient = restClient;
        this.fromAddress = fromAddress;
        this.replyTo = replyTo;
    }

    @Override
    public EmailChannelStatus channelStatus() {
        return EmailChannelStatus.of(true, "RESEND", "Conectado à API Resend");
    }

    @Override
    protected void deliver(String to, String subject, String html, String logPrefix) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", fromAddress);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("html", html);
        if (replyTo != null && !replyTo.isBlank()) {
            body.put("reply_to", replyTo);
        }
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("{}.sent to={}", logPrefix, to);
            meterRegistry.counter("email.sent.total", "type", logPrefix).increment();
        } catch (Exception ex) {
            // O corpo do erro do Resend diz o motivo (domínio não verificado, chave inválida...);
            // só o status ("403 Forbidden") não ajuda quem está configurando.
            String error = ex instanceof RestClientResponseException rex && !rex.getResponseBodyAsString().isBlank()
                    ? rex.getStatusCode().value() + " " + rex.getResponseBodyAsString()
                    : ex.getMessage();
            log.error("{}.failed to={} error={}", logPrefix, to, error);
            meterRegistry.counter("email.failed.total", "type", logPrefix).increment();
            reportFailure(logPrefix, to, error);
            throw new EmailDeliveryException(error);
        }
    }
}
