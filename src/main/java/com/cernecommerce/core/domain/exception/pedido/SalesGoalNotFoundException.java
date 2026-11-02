package com.cernecommerce.core.domain.exception.pedido;

import java.time.YearMonth;

public class SalesGoalNotFoundException extends RuntimeException {
    public SalesGoalNotFoundException(YearMonth month) {
        super("Não há meta cadastrada para " + month);
    }
}
