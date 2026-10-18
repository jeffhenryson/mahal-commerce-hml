package com.cernecommerce.core.domain.exception.pedido;

import java.math.BigDecimal;

/** PED-F006 — troca com diferença a acertar e sem a forma de pagamento da diferença. */
public class SettlementMethodRequiredException extends RuntimeException {
    public SettlementMethodRequiredException(BigDecimal difference) {
        super("Informe a forma de pagamento da diferença de R$ " + difference.abs() + ".");
    }
}
