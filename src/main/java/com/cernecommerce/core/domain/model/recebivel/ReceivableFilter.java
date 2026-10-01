package com.cernecommerce.core.domain.model.recebivel;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Filtros de {@code GET /receivables}. Campo {@code null} é ignorado.
 *
 * @param overdue {@code true}: só em aberto com vencimento antes de hoje
 */
public record ReceivableFilter(Long customerId, ReceivableStatus status, Boolean overdue, LocalDate dueFrom,
        LocalDate dueTo, Instant createdFrom, Instant createdTo) {

    public static ReceivableFilter none() {
        return new ReceivableFilter(null, null, null, null, null, null, null);
    }
}
