package com.cernecommerce.core.ports.out.pedido;

import java.math.BigDecimal;
import java.time.Instant;

/** Agregado das linhas de pedido pago que levaram uma promoção por quantidade. */
public interface PromotionStatsQuery {

    Totals totals(Long promotionId, Instant from, Instant to);

    record Totals(long vendas, long unidades, BigDecimal descontoTotal) {
    }
}
