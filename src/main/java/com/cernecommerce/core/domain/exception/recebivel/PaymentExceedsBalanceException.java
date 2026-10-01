package com.cernecommerce.core.domain.exception.recebivel;

import java.math.BigDecimal;

/** Quitação em débito, crédito ou PIX acima do saldo — só dinheiro pode exceder (vira troco). */
public class PaymentExceedsBalanceException extends RuntimeException {

    public PaymentExceedsBalanceException(BigDecimal nonCash, BigDecimal balance) {
        super("Pagamento fora do dinheiro (" + nonCash + ") acima do saldo em aberto (" + balance + ")");
    }
}
