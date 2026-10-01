package com.cernecommerce.core.domain.model.recebivel;

import java.math.BigDecimal;
import java.time.Instant;

/** Um recebimento no balcão, que pode abater vários recebíveis do mesmo cliente. */
public record ReceivablePaymentBatch(Long id, Long customerId, Long cashSessionId, BigDecimal changeAmount,
        String receivedBy, Instant receivedAt) {
}
