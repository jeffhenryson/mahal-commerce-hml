package com.cernecommerce.core.domain.exception.recebivel;

/** Linha MARCADO em venda anônima ou mesa sem cliente — não há de quem cobrar. */
public class CustomerRequiredForOnAccountException extends RuntimeException {

    public CustomerRequiredForOnAccountException() {
        super("Para marcar, identifique o cliente");
    }
}
