package com.cernecommerce.core.domain.exception.pagamento;

/** Pedido cancelado, reembolsado ou sem pagamento capturado não tem forma de pagamento a corrigir. */
public class OrderNotCorrectableException extends RuntimeException {

    public OrderNotCorrectableException(Long orderId, String why) {
        super("O pagamento do pedido " + orderId + " não pode ser corrigido: " + why);
    }
}
