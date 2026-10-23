package com.cernecommerce.core.domain.exception.ecommerce;

/** Pedido sem atendimento do app — inexistente, ou de outro canal (ECM-F010). */
public class AtendimentoNotFoundException extends RuntimeException {

    public AtendimentoNotFoundException(Long orderId) {
        super("Pedido do app não encontrado: " + orderId);
    }
}
