package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F044 — o sabor do 2º rosh foi pedido para uma linha que não é {@code ROSH_EXTRA}: a sessão, um
 * item de catálogo. O {@code itemId} do {@code PUT …/rosh-sabor} é o do rosh, não o da sessão. 422.
 */
public class ItemNotRoshExtraException extends RuntimeException {
    public ItemNotRoshExtraException(Long itemId, Long comandaId) {
        super("O item " + itemId + " da comanda " + comandaId + " não é um 2º rosh");
    }
}
