package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Quanto de um ganho ({@code earnedEntryId}) um lançamento de resgate ({@code entryId}) consumiu —
 * ou devolveu, com {@code amount} negativo, quando {@code entryId} é um
 * {@link CashbackEntryType#REDEMPTION_REVERSED} (V186).
 *
 * <p>Consumido de um ganho = soma das alocações dele. É o que permite a expiração debitar só a
 * sobra, e a devolução de um resgate saber de quais ganhos o dinheiro saiu.</p>
 */
public record CashbackRedemptionAllocation(Long id, Long entryId, Long earnedEntryId, BigDecimal amount,
        Instant createdAt) {

    public CashbackRedemptionAllocation {
        if (entryId == null || earnedEntryId == null) {
            throw new IllegalArgumentException("entryId e earnedEntryId são obrigatórios");
        }
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("amount não pode ser nulo ou zero");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
    }

    public static CashbackRedemptionAllocation of(Long entryId, Long earnedEntryId, BigDecimal amount, Instant at) {
        return new CashbackRedemptionAllocation(null, entryId, earnedEntryId, amount, at);
    }
}
