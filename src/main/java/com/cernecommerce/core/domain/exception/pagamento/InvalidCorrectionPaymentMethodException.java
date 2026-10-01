package com.cernecommerce.core.domain.exception.pagamento;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;

/** Forma de pagamento que o operador não lança: GATEWAY_PIX (webhook) — PDV-F030. */
public class InvalidCorrectionPaymentMethodException extends RuntimeException {

    public InvalidCorrectionPaymentMethodException(PaymentMethod method) {
        super("Forma de pagamento não permitida na correção: " + method);
    }
}
