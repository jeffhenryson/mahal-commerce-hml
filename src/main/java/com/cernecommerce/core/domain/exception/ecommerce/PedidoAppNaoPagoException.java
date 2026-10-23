package com.cernecommerce.core.domain.exception.ecommerce;

/** Entrega só sai depois do Pix confirmado e liquidado no caixa (ECM-F010). */
public class PedidoAppNaoPagoException extends RuntimeException {

    public PedidoAppNaoPagoException(Long orderId) {
        super("Pedido do app " + orderId + " ainda não foi liquidado no caixa");
    }
}
