package com.cernecommerce.core.domain.model.pagamento;

import java.time.Instant;

/**
 * Uma correção da forma de pagamento de um pedido (PDV-F027): quem, quando, por quê e em que caixa.
 * As linhas que ela aposentou carregam {@code correctionId}; as que ela lançou,
 * {@code originCorrectionId}.
 *
 * @param sessionWasClosed o caixa do pedido já estava fechado — a correção gerou
 *        {@link CashSessionAdjustment}s em vez de mudar o esperado de um caixa aberto
 */
public record OrderPaymentCorrection(Long id, Long orderId, String reason, String correctedBy,
        Instant correctedAt, Long cashSessionId, boolean sessionWasClosed) {

    public OrderPaymentCorrection {
        if (orderId == null || cashSessionId == null) {
            throw new IllegalArgumentException("orderId e cashSessionId são obrigatórios");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason é obrigatório");
        }
        if (correctedBy == null || correctedAt == null) {
            throw new IllegalArgumentException("correctedBy e correctedAt são obrigatórios");
        }
    }
}
