package com.cernecommerce.core.domain.exception.pdv;

import java.math.BigDecimal;

/**
 * PDV-F058 — edição de sessão já paga que mudaria o subtotal: o pedido que a cobrou não muda, então a
 * edição só vale com o mesmo valor. 409.
 */
public class SessionPaidPriceChangeException extends RuntimeException {
    public SessionPaidPriceChangeException(Long itemId, BigDecimal current, BigDecimal edited) {
        super("A sessão " + itemId + " já foi paga: a edição não pode mudar o valor (" + current + " → "
                + edited + ")");
    }
}
