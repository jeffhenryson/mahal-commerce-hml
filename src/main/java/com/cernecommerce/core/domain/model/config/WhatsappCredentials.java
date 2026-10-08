package com.cernecommerce.core.domain.model.config;

/** Credenciais decifradas para chamar a Graph API. Nunca saem do backend. */
public record WhatsappCredentials(String phoneNumberId, String accessToken) {

    @Override
    public String toString() {
        return "WhatsappCredentials[phoneNumberId=" + phoneNumberId + ", accessToken=***]";
    }
}
