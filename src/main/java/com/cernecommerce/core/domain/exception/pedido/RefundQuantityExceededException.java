package com.cernecommerce.core.domain.exception.pedido;

import java.math.BigDecimal;

/**
 * PED-F004 — pediram para estornar mais unidades de um SKU do que o pedido ainda tem: vendidas menos
 * as já estornadas. Vira 422 {@code REFUND_QUANTITY_EXCEEDED}.
 */
public class RefundQuantityExceededException extends RuntimeException {

    private final String sku;
    private final BigDecimal requested;
    private final BigDecimal available;

    public RefundQuantityExceededException(String sku, BigDecimal requested, BigDecimal available) {
        super("Estorno de " + requested.stripTrailingZeros().toPlainString() + " de " + sku + ": restam "
                + available.stripTrailingZeros().toPlainString() + " para estornar neste pedido.");
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
