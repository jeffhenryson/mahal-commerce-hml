package com.cernecommerce.core.domain.exception.recebivel;

/** Mais de uma linha MARCADO na mesma venda. */
public class DuplicateOnAccountPaymentException extends RuntimeException {

    public DuplicateOnAccountPaymentException() {
        super("Só pode haver uma linha MARCADO por venda");
    }
}
