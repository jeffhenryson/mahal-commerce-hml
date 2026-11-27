package com.cernecommerce.core.domain.model.estoque;

/** Situação de uma solicitação: só as {@code ACTIVE} entram nos agregados e nos avisos. */
public enum ProductRequestEntryStatus {
    ACTIVE, CANCELLED, FULFILLED
}
