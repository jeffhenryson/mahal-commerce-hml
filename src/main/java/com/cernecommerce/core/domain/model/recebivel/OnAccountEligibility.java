package com.cernecommerce.core.domain.model.recebivel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pré-checagem do "Marcar" para a UI (CRM-F010).
 *
 * @param reasons códigos do bloqueio: {@code CUSTOMER_NOT_ELIGIBLE}, {@code CUSTOMER_HAS_OVERDUE},
 *        {@code CREDIT_LIMIT_EXCEEDED} (sem limite disponível); vazio quando {@code eligible}
 */
public record OnAccountEligibility(boolean eligible, List<String> reasons, BigDecimal creditLimit,
        BigDecimal openBalance, BigDecimal overdueBalance, BigDecimal available, LocalDate defaultDueDate) {
}
