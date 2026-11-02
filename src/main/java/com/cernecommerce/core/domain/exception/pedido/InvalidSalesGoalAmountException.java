package com.cernecommerce.core.domain.exception.pedido;

/** Meta com valor zero, negativo ou com mais de 2 casas decimais. */
public class InvalidSalesGoalAmountException extends RuntimeException {
    public InvalidSalesGoalAmountException() {
        super("A meta precisa ser maior que zero, com no máximo 2 casas decimais");
    }
}
