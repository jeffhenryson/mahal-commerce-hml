package com.cernecommerce.core.domain.exception.pagamento;

/**
 * O caixa do pedido já fechou e quem corrige não tem {@code ORDER_PAYMENT_CORRECT_CLOSED}
 * (PDV-F030).
 */
public class CashSessionClosedForCorrectionException extends RuntimeException {

    public CashSessionClosedForCorrectionException(Long sessionId) {
        super("O caixa #" + sessionId + " deste pedido já foi fechado; a correção exige permissão de gerente");
    }
}
