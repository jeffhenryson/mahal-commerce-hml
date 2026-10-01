package com.cernecommerce.core.domain.exception.recebivel;

import com.cernecommerce.core.domain.model.recebivel.ReceivableStatus;

/** Operação que exige saldo em aberto num recebível QUITADO ou CANCELADO, ou de outro cliente. */
public class ReceivableNotOpenException extends RuntimeException {

    public ReceivableNotOpenException(Long id, ReceivableStatus status) {
        super("O marcado " + id + " não está em aberto: " + status);
    }

    public ReceivableNotOpenException(String message) {
        super(message);
    }
}
