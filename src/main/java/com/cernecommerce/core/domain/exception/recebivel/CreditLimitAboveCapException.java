package com.cernecommerce.core.domain.exception.recebivel;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * PDV-F046 — o vendedor (só {@code RECEIVABLE_LIMIT_SET_CAPPED}) tentou dar um limite acima do teto
 * {@code pdv.on-account.attendant-max-credit-limit}. Vira 422 {@code CREDIT_LIMIT_ABOVE_CAP}.
 */
public class CreditLimitAboveCapException extends RuntimeException {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final BigDecimal cap;

    public CreditLimitAboveCapException(BigDecimal cap) {
        super("Seu limite máximo é " + String.format(PT_BR, "R$ %,.2f", cap)
                + ". Valores maiores só com um gerente.");
        this.cap = cap;
    }

    public BigDecimal getCap() {
        return cap;
    }
}
