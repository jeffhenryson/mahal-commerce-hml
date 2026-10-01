package com.cernecommerce.core.domain.model.recebivel;

import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Quanto de um recebimento abateu um recebível. {@code amount} é o abatido — o troco do
 * recebimento em dinheiro fica no {@link ReceivablePaymentBatch}, não aqui.
 */
public record ReceivablePayment(Long id, Long receivableId, Long batchId, Long customerId, BigDecimal amount,
        PaymentMethod method, Integer installments, PaymentChannel channel, PaymentProvider provider,
        Long cashSessionId, String receivedBy, Instant receivedAt) {

    public ReceivablePayment {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount deve ser maior que zero");
        }
    }
}
