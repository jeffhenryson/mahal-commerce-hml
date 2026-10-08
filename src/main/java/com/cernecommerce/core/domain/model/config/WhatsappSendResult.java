package com.cernecommerce.core.domain.model.config;

/**
 * Resultado de uma chamada à Graph API. Nunca representa exceção: o adapter devolve a recusa da
 * Meta em {@code error}. {@code messageId} é o {@code wamid} quando a mensagem foi aceita.
 */
public record WhatsappSendResult(boolean success, String error, String messageId) {

    public static WhatsappSendResult ok(String messageId) {
        return new WhatsappSendResult(true, null, messageId);
    }

    public static WhatsappSendResult failed(String error) {
        return new WhatsappSendResult(false, error, null);
    }
}
