package com.cernecommerce.core.domain.exception.pdv;

/** Não há para quem mandar o comprovante: pedido cancelado, sem cliente vinculado ou cliente sem e-mail. */
public class ReceiptEmailUnavailableException extends RuntimeException {

    public ReceiptEmailUnavailableException(Long orderId, String reason) {
        super("Comprovante do pedido " + orderId + " não pode ser enviado por e-mail: " + reason);
    }
}
