package com.cernecommerce.core.domain.exception.pagamento;

/**
 * Pedido pago pelo gateway (app), fora de caixa: quem confirmou foi o webhook, não o operador, e
 * não há gaveta a reconciliar (PDV-F027).
 */
public class GatewayPaymentNotCorrectableException extends RuntimeException {

    public GatewayPaymentNotCorrectableException(Long orderId) {
        super("O pedido " + orderId + " foi pago pelo gateway e não tem caixa; não há o que corrigir");
    }
}
