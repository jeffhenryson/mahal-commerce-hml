package com.cernecommerce.core.domain.exception.pagamento;

import java.math.BigDecimal;

/** Correção de pagamento cuja soma difere do que o cliente pagou de fato (PDV-F030). */
public class PaymentTotalMismatchException extends RuntimeException {

    public PaymentTotalMismatchException(BigDecimal informed, BigDecimal totalPayable) {
        super("A soma dos pagamentos (" + informed + ") tem que ser igual ao total pago ("
                + totalPayable + "), sem troco");
    }
}
