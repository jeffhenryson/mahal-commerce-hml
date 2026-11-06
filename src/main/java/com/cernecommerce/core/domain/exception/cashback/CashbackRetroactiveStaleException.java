package com.cernecommerce.core.domain.exception.cashback;

import java.math.BigDecimal;

/** O valor calculado agora difere do que o admin viu na prévia. */
public class CashbackRetroactiveStaleException extends RuntimeException {
    public CashbackRetroactiveStaleException(BigDecimal expected, BigDecimal current) {
        super("O valor do crédito mudou de R$ " + expected + " para R$ " + current + ". Confira a prévia de novo.");
    }
}
