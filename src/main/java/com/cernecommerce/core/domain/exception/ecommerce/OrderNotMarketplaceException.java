package com.cernecommerce.core.domain.exception.ecommerce;

/** Aceite e recusa pela Central só existem para pedido do app (ECM-F012). */
public class OrderNotMarketplaceException extends RuntimeException {

    public OrderNotMarketplaceException(Long orderId) {
        super("Pedido " + orderId + " não é do app");
    }
}
