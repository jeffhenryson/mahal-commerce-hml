package com.cernecommerce.adapter.out.whatsapp;

import com.cernecommerce.core.domain.model.config.WhatsappSendResult;
import com.cernecommerce.core.ports.out.notification.WhatsappPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp Cloud API (Graph API da Meta). Nunca lança: recusa da Meta, timeout e erro de rede viram
 * {@link WhatsappSendResult#failed}, com a mensagem de erro da Meta quando ela vem no corpo
 * ({@code {"error": {"message": ...}}}).
 */
class MetaWhatsappAdapter implements WhatsappPort {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsappAdapter.class);
    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    private final RestClient restClient;

    MetaWhatsappAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public WhatsappSendResult checkPhoneNumber(String phoneNumberId, String accessToken) {
        try {
            restClient.get()
                    .uri("/{id}?fields=display_phone_number", phoneNumberId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .toBodilessEntity();
            return WhatsappSendResult.ok(null);
        } catch (RestClientResponseException ex) {
            log.warn("whatsapp.check.failed status={}", ex.getStatusCode().value());
            return WhatsappSendResult.failed(metaError(ex));
        } catch (Exception ex) {
            log.warn("whatsapp.check.failed error={}", ex.toString());
            return WhatsappSendResult.failed(truncate(ex.getMessage()));
        }
    }

    @Override
    public WhatsappSendResult sendTemplate(String phoneNumberId, String accessToken, String to, String template,
            String language, List<String> bodyParameters) {
        Map<String, Object> templateBody = new LinkedHashMap<>();
        templateBody.put("name", template);
        templateBody.put("language", Map.of("code", language));
        if (bodyParameters != null && !bodyParameters.isEmpty()) {
            templateBody.put("components", List.of(Map.of(
                    "type", "body",
                    "parameters", bodyParameters.stream()
                            .map(text -> Map.of("type", "text", "text", text == null ? "" : text))
                            .toList())));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", to);
        body.put("type", "template");
        body.put("template", templateBody);
        try {
            SendResponse response = restClient.post()
                    .uri("/{id}/messages", phoneNumberId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SendResponse.class);
            String messageId = response == null || response.messages() == null || response.messages().isEmpty()
                    ? null : response.messages().get(0).id();
            return WhatsappSendResult.ok(messageId);
        } catch (RestClientResponseException ex) {
            log.warn("whatsapp.send.failed template={} status={}", template, ex.getStatusCode().value());
            return WhatsappSendResult.failed(metaError(ex));
        } catch (Exception ex) {
            log.warn("whatsapp.send.failed template={} error={}", template, ex.toString());
            return WhatsappSendResult.failed(truncate(ex.getMessage()));
        }
    }

    /** Mensagem de erro da Meta ({@code error.message}); cai no status HTTP quando o corpo não ajuda. */
    private static String metaError(RestClientResponseException ex) {
        try {
            ErrorResponse error = ex.getResponseBodyAs(ErrorResponse.class);
            if (error != null && error.error() != null && error.error().message() != null) {
                return truncate(error.error().message());
            }
        } catch (Exception ignored) {
            // corpo não é JSON da Graph API — usa o status
        }
        return "HTTP " + ex.getStatusCode().value();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MESSAGE_MAX_LENGTH ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH) : message;
    }

    record SendResponse(List<MessageRef> messages) {
    }

    record MessageRef(String id) {
    }

    record ErrorResponse(ErrorBody error) {
    }

    record ErrorBody(String message, Integer code) {
    }
}
