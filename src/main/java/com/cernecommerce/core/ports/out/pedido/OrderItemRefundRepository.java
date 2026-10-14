package com.cernecommerce.core.ports.out.pedido;

import com.cernecommerce.core.domain.model.pedido.OrderItemRefund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Port de saída das linhas de estorno por item (PED-F004). Append-only. */
public interface OrderItemRefundRepository {

    List<OrderItemRefund> saveAll(List<OrderItemRefund> refunds);

    List<OrderItemRefund> findByOrderId(Long orderId);

    /** Unidades já estornadas por {@code orderItemId}; item sem estorno fica fora do mapa. */
    Map<Long, BigDecimal> sumQuantityByOrderItem(Long orderId);
}
