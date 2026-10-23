package com.cernecommerce.core.domain.exception.ecommerce;

/**
 * O pedido do app está com outro caixa (ECM-F010). Quem aceitou é quem liquida e despacha; para
 * mudar de mãos existe o "assumir", que só vale quando aquele caixa já fechou.
 */
public class PedidoAppOutroCaixaException extends RuntimeException {

    public PedidoAppOutroCaixaException(Long orderId, Long sessionId) {
        super("Pedido do app " + orderId + " está com o caixa " + sessionId);
    }
}
