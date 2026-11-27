package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/**
 * Agregados do pedido de cliente, sempre só das solicitações {@code ACTIVE}. Na listagem saem de um
 * {@code GROUP BY} no banco (para ordenar e paginar por eles); no detalhe, de {@link #of} sobre as entradas.
 */
public record ProductRequestStats(
        int entriesCount,
        int totalQuantity,
        ProductRequestUrgency maxUrgency,
        LocalDate earliestNeededBy,
        BigDecimal maxPriceMin,
        BigDecimal maxPriceMax,
        int pendingNotifications) {

    public static final ProductRequestStats EMPTY = new ProductRequestStats(0, 0, null, null, null, null, 0);

    public static ProductRequestStats of(Collection<ProductRequestEntry> entries) {
        var active = entries.stream().filter(ProductRequestEntry::isActive).toList();
        if (active.isEmpty()) {
            return EMPTY;
        }
        return new ProductRequestStats(
                active.size(),
                active.stream().mapToInt(ProductRequestEntry::quantity).sum(),
                active.stream().map(ProductRequestEntry::urgency).max(Comparator.naturalOrder()).orElse(null),
                active.stream().map(ProductRequestEntry::neededBy).filter(Objects::nonNull)
                        .min(Comparator.naturalOrder()).orElse(null),
                active.stream().map(ProductRequestEntry::maxPrice).filter(Objects::nonNull)
                        .min(Comparator.naturalOrder()).orElse(null),
                active.stream().map(ProductRequestEntry::maxPrice).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null),
                (int) active.stream().filter(ProductRequestEntry::awaitsNotification).count());
    }
}
