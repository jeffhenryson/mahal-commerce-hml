package com.cernecommerce.core.domain.model.estoque;

/** Origem estruturada de uma movimentação. {@code id} pode ser nulo (ver {@link StockMovementOriginType}). */
public record StockMovementOrigin(StockMovementOriginType type, Long id) {

    public StockMovementOrigin {
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
    }

    public static StockMovementOrigin order(Long orderId) {
        return new StockMovementOrigin(StockMovementOriginType.ORDER, orderId);
    }

    public static StockMovementOrigin comanda(Long comandaId) {
        return new StockMovementOrigin(StockMovementOriginType.COMANDA, comandaId);
    }

    public static StockMovementOrigin manual() {
        return new StockMovementOrigin(StockMovementOriginType.MANUAL, null);
    }
}
