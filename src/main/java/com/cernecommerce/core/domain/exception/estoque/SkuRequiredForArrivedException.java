package com.cernecommerce.core.domain.exception.estoque;

/** Pedido indo para ARRIVED sem SKU vinculado e sem {@code withoutSku} — 400 {@code SKU_REQUIRED_FOR_ARRIVED}. */
public class SkuRequiredForArrivedException extends RuntimeException {
    public SkuRequiredForArrivedException(Long id) {
        super("Vincule o produto do catálogo antes de marcar como chegou, ou informe que ele não vai ao catálogo"
                + " (pedido " + id + ")");
    }
}
