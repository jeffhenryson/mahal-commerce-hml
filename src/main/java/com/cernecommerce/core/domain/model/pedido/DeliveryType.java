package com.cernecommerce.core.domain.model.pedido;

/** Como a mercadoria de uma venda chega ao cliente (PDV-F022). */
public enum DeliveryType {
    /**
     * O cliente retira na loja. Retirada imediata nasce {@link OrderStatus#CONCLUIDO}; só com
     * {@code reserveForPickup=true} (cliente volta depois) nasce {@link OrderStatus#RESERVADO}.
     */
    RETIRADA,
    /** A loja leva até o endereço — a venda nasce {@link OrderStatus#RESERVADO} e segue a esteira de expedição. */
    ENTREGA
}
