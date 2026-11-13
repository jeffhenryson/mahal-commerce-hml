package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * "Valor recebido por fora" de um caixa: Pix ou cartão que caiu na maquininha ou na conta sem passar
 * pelo sistema. É só para a conferência por operadora: não entra no esperado da gaveta nem no total
 * recebido. Não se edita; corrigir é apagar (lógico) e lançar de novo.
 */
public record OffSystemReceipt(Long id, Long sessionId, BigDecimal amount, PaymentMethod method,
        PaymentProvider provider, String note, String username, Instant createdAt) {

    public static final int NOTE_MAX_LENGTH = 120;
    public static final Set<PaymentMethod> ALLOWED_METHODS =
            Set.of(PaymentMethod.PIX, PaymentMethod.DEBITO, PaymentMethod.CREDITO);

    public OffSystemReceipt {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount deve ser maior que zero");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("amount tem no máximo 2 casas decimais");
        }
        if (method == null || !ALLOWED_METHODS.contains(method)) {
            throw new IllegalArgumentException("method deve ser PIX, DEBITO ou CREDITO");
        }
        note = note == null || note.isBlank() ? null : note.trim();
        if (note != null && note.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("note tem no máximo " + NOTE_MAX_LENGTH + " caracteres");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username é obrigatório");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
    }

    /** Lançamento novo, no instante atual. */
    public static OffSystemReceipt register(Long sessionId, BigDecimal amount, PaymentMethod method,
            PaymentProvider provider, String note, String username) {
        return new OffSystemReceipt(null, sessionId, amount, method, provider, note, username, Instant.now());
    }
}
