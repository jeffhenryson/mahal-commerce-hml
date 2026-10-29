package com.cernecommerce.core.domain.exception.ecommerce;

/** O aceite é um só: o pedido do app já foi aceito por um caixa (ECM-F012). */
public class OrderAlreadyAcceptedException extends RuntimeException {

    public OrderAlreadyAcceptedException(Long orderId) {
        super("Pedido do app " + orderId + " já foi aceito");
    }
}
