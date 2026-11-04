package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lançamento no ledger de cashback de um cliente (CRM-F003, §2.4 do plano de arquitetura).
 *
 * <h2>Ledger, nunca saldo mutável</h2>
 * <p>Nenhuma linha é atualizada depois de escrita; nenhuma é deletada. Saldo é {@code SUM(amount)}
 * sobre as entradas já liberadas — ver a nota sobre {@link #availableAt} abaixo. Um saldo mutável
 * é impossível de auditar quando o cliente reclama, e reconstruir o extrato depois do fato não é
 * feito — é adivinhado.</p>
 *
 * <h2>Por que não existe uma coluna "status"</h2>
 * <p>Uma entrada {@link CashbackEntryType#EARNED} está "pendente" enquanto {@link #availableAt}
 * não chegou e "expirada" quando uma entrada {@link CashbackEntryType#EXPIRED} referenciando-a (via
 * {@link #reversesEntryId}) existir — nenhuma das duas transições precisa mutar a linha original.
 * Por isso o saldo disponível é sempre {@code SUM(amount) WHERE available_at <= now()}: cobre
 * {@link CashbackEntryType#EARNED} (positivo) e os futuros {@link CashbackEntryType#REDEEMED}/
 * {@link CashbackEntryType#REVERSED}/{@link CashbackEntryType#EXPIRED} (negativos, com
 * {@code availableAt == createdAt} porque um débito tem que valer imediatamente).</p>
 *
 * @param orderItemId item de origem do ganho; {@code null} para lançamentos que não nascem de um
 *        item específico (ex.: ajuste manual, quando essa fatia existir).
 * @param availableAt instante a partir do qual o valor entra no saldo disponível. Para
 *        {@link CashbackEntryType#EARNED}, {@code paidAt + carência}. Para os demais tipos,
 *        igual a {@link #createdAt} — o débito é imediato.
 * @param expiresAt só relevante para {@link CashbackEntryType#EARNED}: instante em que o valor
 *        vence se não for resgatado. {@code null} para os demais tipos.
 * @param reversesEntryId entrada original que este lançamento reverte/expira/devolve.
 *        Obrigatório para todo tipo que não seja {@link CashbackEntryType#EARNED} nem
 *        {@link CashbackEntryType#REDEEMED} — o resgate consome vários ganhos, e o vínculo com
 *        eles mora em {@link CashbackRedemptionAllocation}.
 */
public record CashbackEntry(Long id, Long customerId, Long orderId, Long orderItemId,
        CashbackEntryType type, BigDecimal amount, Instant availableAt, Instant expiresAt,
        Long reversesEntryId, Instant createdAt) {

    public CashbackEntry {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId é obrigatório");
        }
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("amount não pode ser nulo ou zero");
        }
        boolean shouldBePositive = type.isCredit();
        if (shouldBePositive != (amount.signum() > 0)) {
            throw new IllegalArgumentException(
                    "amount de " + type + " deve ser " + (shouldBePositive ? "positivo" : "negativo"));
        }
        if (availableAt == null) {
            throw new IllegalArgumentException("availableAt é obrigatório");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
        if (type != CashbackEntryType.EARNED && type != CashbackEntryType.REDEEMED && reversesEntryId == null) {
            throw new IllegalArgumentException("reversesEntryId é obrigatório para " + type);
        }
        if (type == CashbackEntryType.EARNED && expiresAt != null && !expiresAt.isAfter(availableAt)) {
            throw new IllegalArgumentException("expiresAt deve ser posterior a availableAt");
        }
    }

    /** Ganho de cashback sobre um item de pedido, ainda em carência até {@code availableAt}. */
    public static CashbackEntry earned(Long customerId, Long orderId, Long orderItemId,
            BigDecimal amount, Instant createdAt, Instant availableAt, Instant expiresAt) {
        return new CashbackEntry(null, customerId, orderId, orderItemId, CashbackEntryType.EARNED,
                amount, availableAt, expiresAt, null, createdAt);
    }

    /**
     * Expira uma entrada {@link CashbackEntryType#EARNED} não resgatada: lançamento negativo,
     * disponível imediatamente, referenciando a entrada original.
     */
    public static CashbackEntry expired(CashbackEntry original, Instant expiredAt) {
        return expired(original, original.amount(), expiredAt);
    }

    /**
     * Expira só a {@code remaining} do ganho — a parte que nenhum resgate consumiu (V186). Expirar o
     * valor cheio de um ganho já resgatado debitaria o mesmo dinheiro duas vezes.
     */
    public static CashbackEntry expired(CashbackEntry original, BigDecimal remaining, Instant expiredAt) {
        if (original.type() != CashbackEntryType.EARNED) {
            throw new IllegalArgumentException("só se expira uma entrada EARNED");
        }
        if (remaining == null || remaining.signum() <= 0 || remaining.compareTo(original.amount()) > 0) {
            throw new IllegalArgumentException("remaining deve estar entre 0 (exclusivo) e o valor do ganho");
        }
        return new CashbackEntry(null, original.customerId(), original.orderId(),
                original.orderItemId(), CashbackEntryType.EXPIRED, remaining.negate(),
                expiredAt, null, original.id(), expiredAt);
    }

    /**
     * Resgate de {@code amount} (positivo) do saldo do cliente no pedido {@code orderId}: débito
     * imediato. Os ganhos consumidos são registrados à parte, em {@link CashbackRedemptionAllocation}.
     */
    public static CashbackEntry redeemed(Long customerId, Long orderId, BigDecimal amount, Instant at) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("valor do resgate deve ser positivo");
        }
        return new CashbackEntry(null, customerId, orderId, null, CashbackEntryType.REDEEMED,
                amount.negate(), at, null, null, at);
    }

    /**
     * Devolve {@code credit} (positivo) de um {@link CashbackEntryType#REDEEMED} — pedido cancelado ou
     * estornado. Crédito imediato, sem novo vencimento: o que volta é a sobra dos ganhos originais,
     * que continuam vencendo nas datas deles.
     */
    public static CashbackEntry redemptionReversed(CashbackEntry redeemed, BigDecimal credit, Instant at) {
        if (redeemed.type() != CashbackEntryType.REDEEMED) {
            throw new IllegalArgumentException("só se devolve uma entrada REDEEMED");
        }
        if (credit == null || credit.signum() <= 0 || credit.compareTo(redeemed.amount().negate()) > 0) {
            throw new IllegalArgumentException("crédito deve estar entre 0 (exclusivo) e o valor resgatado");
        }
        return new CashbackEntry(null, redeemed.customerId(), redeemed.orderId(), null,
                CashbackEntryType.REDEMPTION_REVERSED, credit, at, null, redeemed.id(), at);
    }

    /**
     * Reverte uma entrada {@link CashbackEntryType#EARNED} por cancelamento/reembolso do pedido
     * (PDV-F007): lançamento negativo, disponível imediatamente, referenciando a entrada
     * original. Mesmo molde de {@link #expired} — tipo diferente, mesma forma.
     */
    public static CashbackEntry reversed(CashbackEntry original, Instant reversedAt) {
        if (original.type() != CashbackEntryType.EARNED) {
            throw new IllegalArgumentException("só se reverte uma entrada EARNED");
        }
        // V186 — um ganho revertido ainda em carência não conta no saldo disponível; o débito também
        // não pode contar antes dele, ou o disponível cai abaixo do real (e é ele que o resgate
        // confere). Por isso o débito vale a partir do mais tarde entre agora e a liberação do ganho.
        Instant availableAt = original.availableAt().isAfter(reversedAt) ? original.availableAt() : reversedAt;
        return new CashbackEntry(null, original.customerId(), original.orderId(),
                original.orderItemId(), CashbackEntryType.REVERSED, original.amount().negate(),
                availableAt, null, original.id(), reversedAt);
    }

    /** Reconstitui um lançamento a partir de persistência. */
    public static CashbackEntry of(Long id, Long customerId, Long orderId, Long orderItemId,
            CashbackEntryType type, BigDecimal amount, Instant availableAt, Instant expiresAt,
            Long reversesEntryId, Instant createdAt) {
        return new CashbackEntry(id, customerId, orderId, orderItemId, type, amount, availableAt,
                expiresAt, reversesEntryId, createdAt);
    }

    /** Indica se este lançamento já conta para o saldo disponível no instante informado. */
    public boolean isAvailableAt(Instant moment) {
        return !moment.isBefore(availableAt);
    }
}
