package com.cernecommerce.core.domain.exception.recebivel;

import com.cernecommerce.core.domain.model.recebivel.OnAccountChannel;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Saldo em aberto + o novo marcado passa do limite do cliente — o do canal ou, com {@code channel}
 * nulo, o teto total (a soma dos dois canais).
 */
public class CreditLimitExceededException extends RuntimeException {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final OnAccountChannel channel;
    private final BigDecimal limit;
    private final BigDecimal openBalance;
    private final BigDecimal available;

    public CreditLimitExceededException(OnAccountChannel channel, BigDecimal limit, BigDecimal openBalance,
            BigDecimal available) {
        super(label(channel) + ": cabem " + String.format(PT_BR, "R$ %,.2f", available)
                + " (limite " + String.format(PT_BR, "R$ %,.2f", limit) + ", em aberto "
                + String.format(PT_BR, "R$ %,.2f", openBalance) + ")");
        this.channel = channel;
        this.limit = limit;
        this.openBalance = openBalance;
        this.available = available;
    }

    private static String label(OnAccountChannel channel) {
        if (channel == null) {
            return "Limite total";
        }
        return channel == OnAccountChannel.BALCAO ? "Limite de balcão" : "Limite de mesa";
    }

    /** {@code null} = estourou o teto total. */
    public OnAccountChannel getChannel() {
        return channel;
    }

    public BigDecimal getLimit() {
        return limit;
    }

    public BigDecimal getOpenBalance() {
        return openBalance;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
