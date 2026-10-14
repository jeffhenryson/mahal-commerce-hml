package com.cernecommerce.core.domain.model.pedido;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Unidades estornadas de um item de pedido (PED-F004). Append-only: cada estorno, parcial ou total,
 * grava uma linha por item tocado. A soma de {@code quantity} por item é o {@code refundedQuantity}
 * que limita o próximo estorno.
 *
 * @param amount     dinheiro devolvido por estas unidades: o líquido proporcional do item
 * @param costAmount custo congelado das unidades — a perda, quando {@code destination = DISCARD}
 * @param sessionId  sessão de caixa onde o estorno foi feito; nula se quem estornou não tinha caixa aberto
 */
public record OrderItemRefund(Long id, Long orderId, Long orderItemId, String sku, BigDecimal quantity,
        RefundDestination destination, BigDecimal amount, BigDecimal costAmount, String reason, Long sessionId,
        String createdBy, Instant createdAt) {

    public OrderItemRefund {
        // orderItemId é NOT NULL no banco; aqui não se exige porque item ainda não persistido (testes de
        // unidade, que montam o pedido sem ids) não tem id.
        if (orderId == null) {
            throw new IllegalArgumentException("orderId é obrigatório");
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination é obrigatório");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount não pode ser negativo");
        }
        if (createdBy == null || createdBy.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("createdBy e createdAt são obrigatórios");
        }
    }

    public static OrderItemRefund register(Long orderId, Long orderItemId, String sku, BigDecimal quantity,
            RefundDestination destination, BigDecimal amount, BigDecimal costAmount, String reason, Long sessionId,
            String createdBy, Instant createdAt) {
        return new OrderItemRefund(null, orderId, orderItemId, sku, quantity, destination, amount, costAmount,
                reason, sessionId, createdBy, createdAt);
    }
}
