package com.cernecommerce.core.domain.exception.estoque;

/** Itens ({@code .../kit-items}) só existem em kit pré-montado → 409 {@code KIT_NOT_PRE_MONTADO}. */
public class KitNotPreAssembledException extends RuntimeException {
    public KitNotPreAssembledException(String sku) {
        super("Produto não é kit pré-montado, não tem itens: " + sku);
    }
}
