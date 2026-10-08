package com.cernecommerce.core.domain.model.recebivel;

import java.math.BigDecimal;

/**
 * Os limites individuais de um cliente no "Marcar" (CRM-F010), um por linha de
 * {@code customer_credit_limit}. Campo {@code null} = o cliente não tem linha ali: no canal vale o
 * padrão; no total, não há teto.
 *
 * @param total teto da soma dos dois canais
 */
public record CreditLimits(BigDecimal total, BigDecimal balcao, BigDecimal mesa) {

    public static CreditLimits none() {
        return new CreditLimits(null, null, null);
    }

    /** A linha do canal; {@code channel} nulo devolve o teto total. */
    public BigDecimal of(OnAccountChannel channel) {
        if (channel == null) {
            return total;
        }
        return channel == OnAccountChannel.BALCAO ? balcao : mesa;
    }
}
