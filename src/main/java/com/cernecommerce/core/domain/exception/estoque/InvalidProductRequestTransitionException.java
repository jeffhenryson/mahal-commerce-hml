package com.cernecommerce.core.domain.exception.estoque;

import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;

/**
 * Transição fora da matriz do pedido de cliente, ou item fechado (DONE/CANCELLED) recebendo entrada
 * ou edição — 409 {@code INVALID_STATUS_TRANSITION}.
 */
public class InvalidProductRequestTransitionException extends RuntimeException {
    public InvalidProductRequestTransitionException(ProductRequestStatus from, ProductRequestStatus to) {
        super("Transição inválida do pedido de cliente: " + from + " → " + to);
    }

    public InvalidProductRequestTransitionException(ProductRequestStatus current) {
        super("Pedido de cliente fechado (" + current + ") não aceita alteração");
    }
}
