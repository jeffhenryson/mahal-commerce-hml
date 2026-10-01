package com.cernecommerce.core.domain.model.pagamento;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Divergência registrada num caixa já fechado por uma correção de pagamento (PDV-F030).
 *
 * <p>O esperado gravado no fechamento não é reescrito — ele foi o que o operador conferiu. O delta
 * por método diz quanto aquele fechamento teria sido diferente com a forma certa: positivo, o
 * método ganhou; negativo, perdeu (inclui o troco desfeito, em DINHEIRO).</p>
 */
public record CashSessionAdjustment(Long id, Long sessionId, Long orderId, Long correctionId,
        PaymentMethod method, BigDecimal deltaAmount, String createdBy, Instant createdAt) {

    public CashSessionAdjustment {
        if (deltaAmount == null || deltaAmount.signum() == 0) {
            throw new IllegalArgumentException("deltaAmount não pode ser zero");
        }
    }
}
