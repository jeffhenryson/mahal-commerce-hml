package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.model.Money;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Movimento de dinheiro numa sessão de caixa que não é venda (PDV-F002): sangria ou suprimento.
 *
 * <p>Existe como ledger — linhas que só são acrescentadas — pela mesma razão do
 * {@code stock_movement}: o valor esperado na gaveta é <b>derivado</b> desses lançamentos, e um
 * contador mutável tornaria impossível responder "por que o esperado é esse?" quando o fechamento
 * não bate.</p>
 *
 * <p>{@link #reason} é obrigatório de propósito. Sangria sem motivo é indistinguível de desvio, e o
 * momento de registrar o porquê é agora — não na conferência do fim do dia.</p>
 */
public record CashMovement(Long id, Long sessionId, CashMovementType type, BigDecimal amount,
        String reason, String username, Instant createdAt) {

    public CashMovement {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório");
        }
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
        // O sinal vem do type. Valor negativo seria uma segunda forma de dizer a mesma coisa.
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount deve ser maior que zero: o sinal vem do type");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason é obrigatório: sangria sem motivo é indistinguível de desvio");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username é obrigatório");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
    }

    /** Registra um movimento novo, no instante atual. */
    public static CashMovement register(Long sessionId, CashMovementType type, BigDecimal amount,
            String reason, String username) {
        return new CashMovement(null, sessionId, type, Money.of(amount), reason, username, Instant.now());
    }

    /** Reconstitui a partir de persistência. */
    public static CashMovement of(Long id, Long sessionId, CashMovementType type, BigDecimal amount,
            String reason, String username, Instant createdAt) {
        return new CashMovement(id, sessionId, type, amount, reason, username, createdAt);
    }

    /**
     * Efeito no saldo esperado da gaveta: negativo para sangria, positivo para suprimento. É o valor
     * que o fechamento soma — nunca {@link #amount} cru.
     */
    public BigDecimal signedAmount() {
        return type == CashMovementType.SANGRIA ? amount.negate() : amount;
    }
}
