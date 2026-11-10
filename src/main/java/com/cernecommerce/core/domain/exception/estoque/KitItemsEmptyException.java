package com.cernecommerce.core.domain.exception.estoque;

/** Kit pré-montado precisa de ao menos um item → 422 {@code KIT_ITEMS_EMPTY}. */
public class KitItemsEmptyException extends RuntimeException {
    public KitItemsEmptyException(String sku) {
        super("Kit pré-montado precisa de ao menos um item: " + sku);
    }
}
