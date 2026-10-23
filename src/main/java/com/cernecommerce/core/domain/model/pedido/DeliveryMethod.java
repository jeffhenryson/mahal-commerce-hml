package com.cernecommerce.core.domain.model.pedido;

/** Quem leva uma {@link DeliveryType#ENTREGA} (PDV-F022). */
public enum DeliveryMethod {
    /** Motoboy da loja — {@code courierName}/{@code courierPhone}. */
    MOTOBOY_LOJA,
    /** Corrida pelo app 99 — {@code pickupCode}/{@code dropoffCode}, normalmente preenchidos depois. */
    APP_99,
    /** Corrida pelo Uber Flash (ECM-F009) — mesmos campos da 99, normalmente preenchidos depois. */
    UBER_FLASH,
    /** Correios — {@code trackingCode}, normalmente preenchido depois da postagem. */
    CORREIOS
}
