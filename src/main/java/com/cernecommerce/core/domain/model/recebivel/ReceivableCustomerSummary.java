package com.cernecommerce.core.domain.model.recebivel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma linha de {@code GET /receivables/summary}: o marcado agrupado por cliente. */
public record ReceivableCustomerSummary(Long customerId, String customerName, BigDecimal openBalance,
        BigDecimal overdueBalance, BigDecimal creditLimit, LocalDate nextDueDate, long count) {
}
