package com.cernecommerce.core.domain.exception.pdv;

import java.math.BigDecimal;

/**
 * Sangria maior que o dinheiro que a gaveta deveria ter naquele momento (PDV-C048).
 *
 * <p>Sem esta barreira o erro de digitação ("5000" no lugar de "50,00") só aparecia no fechamento,
 * como uma falta sem relação visível com a sangria que a causou. 409 e não 400: o valor é bem formado;
 * o que impede é o estado da gaveta. A mensagem leva o disponível para a tela mostrar.</p>
 */
public class CashWithdrawalExceedsDrawerException extends RuntimeException {

    private final BigDecimal available;

    public CashWithdrawalExceedsDrawerException(Long sessionId, BigDecimal requested, BigDecimal available) {
        super("Sangria de " + requested + " maior que o dinheiro esperado na gaveta do caixa " + sessionId
                + ": " + available);
        this.available = available;
    }

    public BigDecimal available() {
        return available;
    }
}
