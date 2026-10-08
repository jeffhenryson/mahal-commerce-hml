package com.cernecommerce.core.domain.model.recebivel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pré-checagem do "Marcar" para a UI (CRM-F010).
 *
 * <p>Com canal, {@code creditLimit}, {@code openBalance}, {@code available} e o
 * {@code CREDIT_LIMIT_EXCEEDED} são daquele canal; sem canal, do cliente inteiro.
 * {@code limitsByChannel} vem sempre, com os dois canais.</p>
 *
 * @param reasons códigos do bloqueio: {@code CUSTOMER_NOT_ELIGIBLE}, {@code CUSTOMER_HAS_OVERDUE},
 *        {@code CREDIT_LIMIT_EXCEEDED} (sem limite disponível); vazio quando {@code eligible}
 */
public record OnAccountEligibility(boolean eligible, List<String> reasons, BigDecimal creditLimit,
        BigDecimal openBalance, BigDecimal overdueBalance, BigDecimal available, LocalDate defaultDueDate,
        List<ChannelLimit> limitsByChannel) {

    /**
     * @param creditLimit limite próprio do cliente no canal; {@code null} = vale o padrão
     * @param available quanto ainda cabe, já com o padrão e o teto total
     */
    public record ChannelLimit(OnAccountChannel channel, BigDecimal creditLimit, BigDecimal openBalance,
            BigDecimal available) {
    }
}
