package com.cernecommerce.core.domain.exception.pedido;

import com.cernecommerce.core.domain.model.pedido.OrderStatus;

/**
 * PED-F006 — o pedido não aceita troca: não está pago, já foi cancelado ou totalmente reembolsado, ou
 * é de mesa (o consumo do salão foi servido; o caminho é o estorno). 409: o pedido existe, o estado
 * dele é que impede.
 */
public class OrderNotExchangeableException extends RuntimeException {
    public OrderNotExchangeableException(Long orderId, OrderStatus status) {
        super("O pedido " + orderId + " não aceita troca no status " + status + ".");
    }

    public OrderNotExchangeableException(Long orderId, String motivo) {
        super("O pedido " + orderId + " não aceita troca: " + motivo + ".");
    }
}
