package com.cernecommerce.core.domain.model.pedido;

import java.math.BigDecimal;

/**
 * Unidades de um item a estornar (PED-F004), com o destino EFETIVO e o dinheiro delas.
 *
 * @param index posição do item no pedido. O plano é por posição, e não por id, porque duas linhas do
 *              mesmo SKU são itens distintos, e o pedido ainda não persistido não tem id.
 */
public record RefundPlanLine(int index, OrderItem item, BigDecimal quantity, RefundDestination destination,
        BigDecimal amount) {
}
