package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * O que o crédito retroativo faria, sem gravar nada.
 *
 * @param blockReason          motivo em português quando {@code eligible} é falso
 * @param skippedAlreadyEarned pedidos pagos que ficaram de fora por já terem ganho, mesmo que revertido
 * @param orders               do pedido mais recente para o mais antigo
 */
public record CashbackRetroactivePreview(boolean eligible, String blockReason, BigDecimal percent, int ordersCount,
        BigDecimal baseAmount, BigDecimal creditAmount, int skippedAlreadyEarned, Instant availableAt,
        Instant expiresAt, List<OrderCredit> orders) {

    public record OrderCredit(Long orderId, String orderNumber, Instant paidAt, BigDecimal baseAmount,
            BigDecimal creditAmount) {
    }

    public static CashbackRetroactivePreview blocked(String reason) {
        return new CashbackRetroactivePreview(false, reason, null, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, null, null,
                List.of());
    }
}
