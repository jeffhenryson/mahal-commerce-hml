package com.cernecommerce.core.domain.exception.ecommerce;

/** O atendimento do pedido do app não pode ir para a etapa pedida (ECM-F010). */
public class AtendimentoTransicaoInvalidaException extends RuntimeException {

    private final Long orderId;

    public AtendimentoTransicaoInvalidaException(Long orderId, String message) {
        super(message);
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
