package com.cernecommerce.core.domain.exception.ecommerce;

/** O pedido do app já saiu da fase de aceite: foi pago e despachado, recusado, cancelado ou expirou (ECM-F012). */
public class OrderNotPendingAcceptException extends RuntimeException {

    public OrderNotPendingAcceptException(Long orderId, String situacao) {
        super("Pedido do app " + orderId + " não está aguardando aceite (" + situacao + ")");
    }
}
