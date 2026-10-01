package com.cernecommerce.core.domain.model.pdv;

import java.time.Instant;

/**
 * Filtros de {@code GET /pdv/comandas/history} (PDV-F029). Campo {@code null} é ignorado; sem
 * {@code status}, FECHADA e CANCELADA. {@code from}/{@code to} recortam pelo encerramento.
 */
public record ComandaHistoryFilter(Instant from, Instant to, ComandaStatus status, Long customerId,
        String openedBy, String closedBy, String tableLabel, String warehouseCode) {
}
