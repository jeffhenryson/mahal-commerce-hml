package com.cernecommerce.core.domain.model.pedido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;

/** Meta mensal de faturamento (pendência 9). Uma por mês, valendo para todos os canais. */
public record SalesGoal(YearMonth month, BigDecimal amount, String updatedBy, Instant updatedAt) {

    public SalesGoal {
        if (month == null) {
            throw new IllegalArgumentException("month é obrigatório");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new com.cernecommerce.core.domain.exception.pedido.InvalidSalesGoalAmountException();
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw new com.cernecommerce.core.domain.exception.pedido.InvalidSalesGoalAmountException();
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
