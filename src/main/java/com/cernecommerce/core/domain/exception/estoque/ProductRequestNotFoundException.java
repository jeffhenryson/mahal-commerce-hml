package com.cernecommerce.core.domain.exception.estoque;

/** Pedido de cliente (ou solicitação dele) inexistente — 404 {@code PRODUCT_REQUEST_NOT_FOUND}. */
public class ProductRequestNotFoundException extends RuntimeException {
    public ProductRequestNotFoundException(Long id) {
        super("Pedido de cliente não encontrado: id=" + id);
    }

    public ProductRequestNotFoundException(Long id, Long entryId) {
        super("Solicitação não encontrada: pedido=" + id + ", entrada=" + entryId);
    }
}
