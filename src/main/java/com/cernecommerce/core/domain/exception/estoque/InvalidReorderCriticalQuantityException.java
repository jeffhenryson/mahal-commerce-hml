package com.cernecommerce.core.domain.exception.estoque;

import java.math.BigDecimal;

/** {@code criticalQuantity} do ponto de reposição fora de {@code 0 ≤ c < minQuantity} → 422 {@code REORDER_CRITICAL_INVALID}. */
public class InvalidReorderCriticalQuantityException extends RuntimeException {
    public InvalidReorderCriticalQuantityException(BigDecimal critical, BigDecimal min) {
        super("Quantidade crítica deve ser maior ou igual a zero e menor que o mínimo (" + min.stripTrailingZeros()
                .toPlainString() + "): " + critical.stripTrailingZeros().toPlainString());
    }
}
