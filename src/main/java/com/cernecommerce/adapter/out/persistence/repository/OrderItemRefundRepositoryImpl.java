package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderItemRefundEntity;
import com.cernecommerce.core.domain.model.pedido.OrderItemRefund;
import com.cernecommerce.core.domain.model.pedido.RefundDestination;
import com.cernecommerce.core.ports.out.pedido.OrderItemRefundRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@Transactional
public class OrderItemRefundRepositoryImpl implements OrderItemRefundRepository {

    private final OrderItemRefundJpaRepository jpaRepository;

    public OrderItemRefundRepositoryImpl(OrderItemRefundJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<OrderItemRefund> saveAll(List<OrderItemRefund> refunds) {
        return jpaRepository.saveAll(refunds.stream().map(OrderItemRefundRepositoryImpl::toEntity).toList())
                .stream().map(OrderItemRefundRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderItemRefund> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(OrderItemRefundRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> sumQuantityByOrderItem(Long orderId) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : jpaRepository.sumQuantityByOrderItem(orderId)) {
            result.put((Long) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    private static OrderItemRefundEntity toEntity(OrderItemRefund r) {
        OrderItemRefundEntity e = new OrderItemRefundEntity();
        e.setId(r.id());
        e.setOrderId(r.orderId());
        e.setOrderItemId(r.orderItemId());
        e.setSku(r.sku());
        e.setQuantity(r.quantity());
        e.setDestination(r.destination().name());
        e.setAmount(r.amount());
        e.setCostAmount(r.costAmount());
        e.setReason(r.reason());
        e.setSessionId(r.sessionId());
        e.setCreatedBy(r.createdBy());
        e.setCreatedAt(r.createdAt());
        return e;
    }

    private static OrderItemRefund toDomain(OrderItemRefundEntity e) {
        return new OrderItemRefund(e.getId(), e.getOrderId(), e.getOrderItemId(), e.getSku(), e.getQuantity(),
                RefundDestination.valueOf(e.getDestination()), e.getAmount(), e.getCostAmount(), e.getReason(),
                e.getSessionId(), e.getCreatedBy(), e.getCreatedAt());
    }
}
