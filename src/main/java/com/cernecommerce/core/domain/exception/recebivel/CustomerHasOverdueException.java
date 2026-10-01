package com.cernecommerce.core.domain.exception.recebivel;

import java.math.BigDecimal;

/** O cliente tem marcado vencido — não marca de novo até quitar. */
public class CustomerHasOverdueException extends RuntimeException {

    private final BigDecimal overdueBalance;

    public CustomerHasOverdueException(Long customerId, BigDecimal overdueBalance) {
        super("O cliente " + customerId + " tem " + overdueBalance + " marcado vencido");
        this.overdueBalance = overdueBalance;
    }

    public BigDecimal getOverdueBalance() {
        return overdueBalance;
    }
}
