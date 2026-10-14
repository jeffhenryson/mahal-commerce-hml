package com.cernecommerce.core.domain.exception.pedido;

/**
 * PED-F004 — quem só tem {@code ORDER_REFUND_OWN_SESSION} (o vendedor) estorna apenas pedido da
 * própria sessão de caixa aberta. Vira 403 {@code REFUND_NOT_OWN_SESSION}.
 */
public class RefundNotOwnSessionException extends RuntimeException {

    public RefundNotOwnSessionException(Long orderId, String username) {
        super("O pedido " + orderId + " não é da sessão de caixa aberta de " + username
                + ": estornar pedido de outra sessão só com um gerente.");
    }
}
