package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-C034 — forma de pagamento que o operador não lança: inexistente, ou {@code GATEWAY_PIX}, que
 * só o webhook do gateway captura. Aceito no balcão ou na mesa, o GATEWAY_PIX deixava a venda paga e
 * fora de toda conferência do caixa ({@code payment-totals} e o fechamento o pulam). 400.
 */
public class InvalidPaymentMethodException extends RuntimeException {
    public InvalidPaymentMethodException(String method) {
        super("Forma de pagamento inválida: " + method
                + ". Use DINHEIRO, DEBITO, CREDITO, PIX ou MARCADO.");
    }
}
