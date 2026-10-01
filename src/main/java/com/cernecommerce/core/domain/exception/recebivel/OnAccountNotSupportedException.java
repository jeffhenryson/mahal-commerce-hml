package com.cernecommerce.core.domain.exception.recebivel;

/** MARCADO num caminho que não o aceita (liquidação de pedido do app, correção, quitação). */
public class OnAccountNotSupportedException extends RuntimeException {

    public OnAccountNotSupportedException(String where) {
        super("MARCADO não é aceito em " + where);
    }
}
