package com.cernecommerce.core.domain.exception.pdv;

import java.math.BigDecimal;

/**
 * PDV-F056 — o troco deixado na gaveta sai do que foi contado: não pode passar dele. 400: o pedido é
 * que está errado, não o estado do caixa.
 */
public class LeftInDrawerExceedsCountedException extends RuntimeException {
    public LeftInDrawerExceedsCountedException(BigDecimal leftInDrawer, BigDecimal counted) {
        super("O troco deixado na gaveta (" + leftInDrawer + ") não pode passar do contado (" + counted + ").");
    }
}
