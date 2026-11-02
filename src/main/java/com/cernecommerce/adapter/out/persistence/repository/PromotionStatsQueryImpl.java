package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.ports.out.pedido.PromotionStatsQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
@Transactional(readOnly = true)
public class PromotionStatsQueryImpl implements PromotionStatsQuery {

    private final OrderItemJpaRepository orderItemJpaRepository;

    public PromotionStatsQueryImpl(OrderItemJpaRepository orderItemJpaRepository) {
        this.orderItemJpaRepository = orderItemJpaRepository;
    }

    @Override
    public Totals totals(Long promotionId, Instant from, Instant to) {
        OrderItemJpaRepository.PromotionStatsProjection p = orderItemJpaRepository.findPromotionStats(promotionId,
                from, to);
        BigDecimal unidades = p.getUnidades() == null ? BigDecimal.ZERO : p.getUnidades();
        return new Totals(p.getVendas(), unidades.longValue(), p.getDescontoTotal());
    }
}
