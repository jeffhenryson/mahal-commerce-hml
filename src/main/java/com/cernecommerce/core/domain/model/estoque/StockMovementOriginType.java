package com.cernecommerce.core.domain.model.estoque;

/**
 * De onde veio uma movimentação de estoque. O {@code originId} que acompanha cada tipo aponta para:
 * <ul>
 *   <li>{@link #ORDER}: {@code orders.id} (venda de balcão, cancelamento/estorno que devolve ao estoque);</li>
 *   <li>{@link #COMANDA}: {@code comanda.id} (lançamento e estorno na mesa);</li>
 *   <li>{@link #GOODS_RECEIPT}: {@code goods_receipt.id};</li>
 *   <li>{@link #RESERVATION}: {@code stock_reservation.id} (consumo da reserva do marketplace);</li>
 *   <li>{@link #TRANSFER}, {@link #PACKAGING}, {@link #MANUAL}: sem id.</li>
 * </ul>
 */
public enum StockMovementOriginType {
    ORDER,
    COMANDA,
    GOODS_RECEIPT,
    RESERVATION,
    TRANSFER,
    PACKAGING,
    MANUAL
}
