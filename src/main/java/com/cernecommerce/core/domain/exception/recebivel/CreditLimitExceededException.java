package com.cernecommerce.core.domain.exception.recebivel;

import java.math.BigDecimal;

/** Saldo em aberto + o novo marcado passa do limite do cliente. */
public class CreditLimitExceededException extends RuntimeException {

    private final BigDecimal limit;
    private final BigDecimal openBalance;
    private final BigDecimal available;

    public CreditLimitExceededException(BigDecimal limit, BigDecimal openBalance, BigDecimal available) {
        super("Limite de crédito excedido: limite " + limit + ", em aberto " + openBalance
                + ", disponível " + available);
        this.limit = limit;
        this.openBalance = openBalance;
        this.available = available;
    }

    public BigDecimal getLimit() {
        return limit;
    }

    public BigDecimal getOpenBalance() {
        return openBalance;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
