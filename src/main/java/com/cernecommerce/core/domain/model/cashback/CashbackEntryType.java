package com.cernecommerce.core.domain.model.cashback;

/**
 * Tipo de lançamento no ledger de cashback (CRM-F003), append-only.
 *
 * <ul>
 *   <li>{@link #EARNED} — ganho (positivo), com carência e vencimento.</li>
 *   <li>{@link #REDEEMED} — resgate (negativo) num pedido; os ganhos que ele consumiu ficam em
 *       {@link CashbackRedemptionAllocation} (V186).</li>
 *   <li>{@link #REVERSED} — anula um ganho por cancelamento/estorno do pedido de origem.</li>
 *   <li>{@link #EXPIRED} — anula a sobra não resgatada de um ganho vencido.</li>
 *   <li>{@link #REDEMPTION_REVERSED} — devolve (positivo) um resgate cujo pedido foi cancelado ou
 *       estornado.</li>
 * </ul>
 */
public enum CashbackEntryType {
    EARNED,
    REDEEMED,
    REVERSED,
    EXPIRED,
    REDEMPTION_REVERSED;

    /** Tipos gravados com valor positivo — todos os demais são débitos. */
    public boolean isCredit() {
        return this == EARNED || this == REDEMPTION_REVERSED;
    }
}
