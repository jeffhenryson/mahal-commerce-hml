package com.cernecommerce.core.ports.out.pedido;

import com.cernecommerce.core.domain.model.pedido.OrderItemExchange;

import java.util.List;

/** Port de saída do histórico de trocas de item (PED-F006). Append-only. */
public interface OrderItemExchangeRepository {

    OrderItemExchange save(OrderItemExchange exchange);

    List<OrderItemExchange> findByOrderId(Long orderId);
}
