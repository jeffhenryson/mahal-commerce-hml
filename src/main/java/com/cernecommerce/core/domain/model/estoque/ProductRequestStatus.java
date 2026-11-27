package com.cernecommerce.core.domain.model.estoque;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo do pedido de cliente: {@code PENDING → ORDERED → ARRIVED → DONE}, com {@code CANCELLED} a partir de
 * qualquer status aberto. {@code PENDING → ARRIVED} vale (apareceu sem encomenda) e {@code ORDERED → PENDING}
 * também (a encomenda furou). {@code DONE} e {@code CANCELLED} são finais: reabrir está fora do escopo.
 */
public enum ProductRequestStatus {
    PENDING, ORDERED, ARRIVED, DONE, CANCELLED;

    private static final Map<ProductRequestStatus, Set<ProductRequestStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(ORDERED, ARRIVED, CANCELLED),
            ORDERED, EnumSet.of(PENDING, ARRIVED, CANCELLED),
            ARRIVED, EnumSet.of(DONE, CANCELLED),
            DONE, EnumSet.noneOf(ProductRequestStatus.class),
            CANCELLED, EnumSet.noneOf(ProductRequestStatus.class));

    public static final Set<ProductRequestStatus> OPEN = EnumSet.of(PENDING, ORDERED, ARRIVED);

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean isFinal() {
        return !isOpen();
    }

    public boolean canMoveTo(ProductRequestStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
