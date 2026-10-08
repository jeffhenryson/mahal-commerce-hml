package com.cernecommerce.core.domain.model.crm;

import java.net.URI;
import java.util.Map;

/**
 * Como uma automação entrega o disparo: o destino, o que cada destino precisa e o segredo de
 * autenticação do webhook próprio.
 *
 * <p>{@code webhookHeaders} é o segredo em claro — só existe em memória; a persistência o grava
 * cifrado e a API nunca o devolve, só {@code authLast4}.</p>
 */
public record AutomationDelivery(
        AutomationDestination destino,
        String webhookUrl,
        String workflowPath,
        String whatsappTemplate,
        String whatsappIdioma,
        AutomationAuthType authTipo,
        String authHeaderNome,
        String authLast4,
        Map<String, String> webhookHeaders) {

    public static final String DEFAULT_WHATSAPP_LANGUAGE = "pt_BR";
    private static final String BEARER_PREFIX = "Bearer ";

    public AutomationDelivery {
        destino = destino == null ? AutomationDestination.WEBHOOK : destino;
        authTipo = authTipo == null ? AutomationAuthType.NONE : authTipo;
        webhookUrl = blankToNull(webhookUrl);
        workflowPath = blankToNull(workflowPath);
        whatsappTemplate = blankToNull(whatsappTemplate);
        whatsappIdioma = blankToNull(whatsappIdioma);
        authHeaderNome = blankToNull(authHeaderNome);
        webhookHeaders = webhookHeaders == null ? Map.of() : Map.copyOf(webhookHeaders);
        if (webhookUrl != null) {
            try {
                URI.create(webhookUrl);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("webhookUrl possui formato inválido");
            }
        }
    }

    /** Webhook próprio sem autenticação — o formato das automações antigas. */
    public static AutomationDelivery webhook(String webhookUrl, Map<String, String> webhookHeaders) {
        return new AutomationDelivery(AutomationDestination.WEBHOOK, webhookUrl, null, null, null,
                inferAuthType(webhookHeaders), inferHeaderName(webhookHeaders), last4Of(webhookHeaders),
                webhookHeaders);
    }

    /**
     * Cópia com um segredo novo (ou o atual, quando {@code newHeaders} é {@code null}). Sem
     * autenticação ({@link AutomationAuthType#NONE}) o segredo é sempre descartado.
     */
    public AutomationDelivery withSecret(Map<String, String> newHeaders) {
        Map<String, String> headers = authTipo == AutomationAuthType.NONE ? Map.of()
                : newHeaders == null ? webhookHeaders : newHeaders;
        String last4 = newHeaders == null && authTipo != AutomationAuthType.NONE ? authLast4 : last4Of(headers);
        return new AutomationDelivery(destino, webhookUrl, workflowPath, whatsappTemplate, whatsappIdioma, authTipo,
                authHeaderNome, last4, headers);
    }

    public String whatsappIdiomaOrDefault() {
        return whatsappIdioma == null ? DEFAULT_WHATSAPP_LANGUAGE : whatsappIdioma;
    }

    /** O que o destino exige para poder disparar; {@code null} quando está completo. */
    public String missingRequirement() {
        return switch (destino) {
            case WEBHOOK -> webhookUrl == null ? "webhookUrl é obrigatório para o destino WEBHOOK" : null;
            case PLATAFORMA -> workflowPath == null ? "workflowPath é obrigatório para o destino PLATAFORMA" : null;
            case WHATSAPP_META -> whatsappTemplate == null
                    ? "whatsappTemplate é obrigatório para o destino WHATSAPP_META" : null;
        };
    }

    /** 4 últimos caracteres do segredo — sem o prefixo "Bearer ", quando houver. */
    static String last4Of(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String value = headers.values().iterator().next();
        if (value == null || value.isBlank()) {
            return null;
        }
        String secret = value.startsWith(BEARER_PREFIX) ? value.substring(BEARER_PREFIX.length()) : value;
        return secret.substring(Math.max(0, secret.length() - 4));
    }

    private static AutomationAuthType inferAuthType(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return AutomationAuthType.NONE;
        }
        String value = headers.get("Authorization");
        return headers.size() == 1 && value != null && value.startsWith(BEARER_PREFIX)
                ? AutomationAuthType.BEARER : AutomationAuthType.HEADER;
    }

    private static String inferHeaderName(Map<String, String> headers) {
        return inferAuthType(headers) == AutomationAuthType.HEADER ? headers.keySet().iterator().next() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString() {
        return "AutomationDelivery[destino=" + destino + ", webhookUrl=" + webhookUrl + ", workflowPath=" + workflowPath
                + ", whatsappTemplate=" + whatsappTemplate + ", authTipo=" + authTipo + ", authLast4=" + authLast4
                + ", webhookHeaders=" + (webhookHeaders.isEmpty() ? "{}" : "***") + "]";
    }
}
