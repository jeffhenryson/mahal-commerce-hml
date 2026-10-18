package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Troca de um item do pedido por outro produto (PED-F006). Append-only: é o histórico que liga a perna
 * devolvida (uma linha de {@link OrderItemRefund} com {@code exchangeId}) ao item entregue no mesmo
 * pedido e à linha de pagamento da diferença.
 *
 * @param returnedAmount  líquido proporcional das unidades devolvidas — o mesmo unitário do estorno
 * @param newAmount       preço efetivo atual do novo SKU × quantidade, sem desconto
 * @param difference      {@code newAmount − returnedAmount}: positiva o cliente paga, negativa recebe
 * @param settlementMethod forma da diferença; nula quando a diferença é zero
 * @param paymentId       a linha CAPTURED (diferença a pagar) ou REFUNDED (a receber); nula sem diferença
 * @param sessionId       caixa de quem trocou; nulo quando ninguém tinha caixa aberto e nada se moveu
 */
public record OrderItemExchange(Long id, Long orderId, Long returnedOrderItemId, String sku, BigDecimal quantity,
        RefundDestination destination, Long newOrderItemId, String newSku, BigDecimal newQuantity,
        BigDecimal returnedAmount, BigDecimal newAmount, BigDecimal difference, PaymentMethod settlementMethod,
        Long paymentId, Long sessionId, String reason, String createdBy, Instant createdAt) {

    public OrderItemExchange {
        if (orderId == null || returnedOrderItemId == null || newOrderItemId == null) {
            throw new IllegalArgumentException("orderId, returnedOrderItemId e newOrderItemId são obrigatórios");
        }
        if (quantity == null || quantity.signum() <= 0 || newQuantity == null || newQuantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity e newQuantity devem ser maiores que zero");
        }
        if (destination == null || destination == RefundDestination.CUSTOMER_KEEPS) {
            throw new IllegalArgumentException("destino da troca é RESTOCK ou DISCARD");
        }
        if (difference == null || newAmount == null || returnedAmount == null
                || difference.compareTo(newAmount.subtract(returnedAmount)) != 0) {
            throw new IllegalArgumentException("difference deve ser newAmount - returnedAmount");
        }
        if (difference.signum() != 0 && settlementMethod == null) {
            throw new IllegalArgumentException("diferença diferente de zero exige settlementMethod");
        }
        if (createdBy == null || createdBy.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("createdBy e createdAt são obrigatórios");
        }
    }

    public static OrderItemExchange register(Long orderId, Long returnedOrderItemId, String sku, BigDecimal quantity,
            RefundDestination destination, Long newOrderItemId, String newSku, BigDecimal newQuantity,
            BigDecimal returnedAmount, BigDecimal newAmount, PaymentMethod settlementMethod, Long paymentId,
            Long sessionId, String reason, String createdBy, Instant createdAt) {
        BigDecimal difference = newAmount.subtract(returnedAmount);
        return new OrderItemExchange(null, orderId, returnedOrderItemId, sku, quantity, destination, newOrderItemId,
                newSku, newQuantity, returnedAmount, newAmount, difference,
                difference.signum() == 0 ? null : settlementMethod, paymentId, sessionId, reason, createdBy,
                createdAt);
    }
}
