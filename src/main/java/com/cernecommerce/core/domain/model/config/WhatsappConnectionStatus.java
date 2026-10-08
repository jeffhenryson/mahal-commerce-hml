package com.cernecommerce.core.domain.model.config;

/** Se o token salvo alcança o número na Graph API; {@code detail} explica a falha. */
public record WhatsappConnectionStatus(boolean connected, String detail) {

    public static WhatsappConnectionStatus up() {
        return new WhatsappConnectionStatus(true, null);
    }

    public static WhatsappConnectionStatus disconnected(String detail) {
        return new WhatsappConnectionStatus(false, detail);
    }
}
