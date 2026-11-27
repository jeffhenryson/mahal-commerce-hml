package com.cernecommerce.core.domain.model.estoque;

/** Tipos do histórico do pedido de cliente ({@code product_request_events}). */
public enum ProductRequestEventType {
    CREATED, ENTRY_ADDED, ENTRY_UPDATED, ENTRY_CANCELLED, STATUS_CHANGED, UPDATED, LINKED_SKU, NOTIFIED
}
