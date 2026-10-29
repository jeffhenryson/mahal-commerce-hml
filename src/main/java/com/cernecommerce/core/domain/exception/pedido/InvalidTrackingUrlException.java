package com.cernecommerce.core.domain.exception.pedido;

/** Link de acompanhamento da entrega que não é http(s) ou passa de 500 caracteres (ECM-F013). */
public class InvalidTrackingUrlException extends RuntimeException {

    public InvalidTrackingUrlException(String message) {
        super(message);
    }
}
