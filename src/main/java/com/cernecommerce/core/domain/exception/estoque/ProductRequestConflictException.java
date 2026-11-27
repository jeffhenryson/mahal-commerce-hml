package com.cernecommerce.core.domain.exception.estoque;

/** Duas pessoas alteraram o mesmo pedido de cliente ao mesmo tempo — 409 {@code PRODUCT_REQUEST_CONFLICT}. */
public class ProductRequestConflictException extends RuntimeException {
    public ProductRequestConflictException(Long id) {
        super("O pedido de cliente " + id + " foi alterado por outra pessoa. Recarregue e tente de novo.");
    }
}
