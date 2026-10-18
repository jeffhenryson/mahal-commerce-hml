package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderItemExchangeEntity;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.OrderItemExchange;
import com.cernecommerce.core.domain.model.pedido.RefundDestination;
import com.cernecommerce.core.ports.out.pedido.OrderItemExchangeRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class OrderItemExchangeRepositoryImpl implements OrderItemExchangeRepository {

    private final OrderItemExchangeJpaRepository jpaRepository;

    public OrderItemExchangeRepositoryImpl(OrderItemExchangeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OrderItemExchange save(OrderItemExchange exchange) {
        return toDomain(jpaRepository.save(toEntity(exchange)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderItemExchange> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(OrderItemExchangeRepositoryImpl::toDomain).toList();
    }

    private static OrderItemExchangeEntity toEntity(OrderItemExchange x) {
        OrderItemExchangeEntity e = new OrderItemExchangeEntity();
        e.setId(x.id());
        e.setOrderId(x.orderId());
        e.setReturnedOrderItemId(x.returnedOrderItemId());
        e.setSku(x.sku());
        e.setQuantity(x.quantity());
        e.setDestination(x.destination().name());
        e.setNewOrderItemId(x.newOrderItemId());
        e.setNewSku(x.newSku());
        e.setNewQuantity(x.newQuantity());
        e.setReturnedAmount(x.returnedAmount());
        e.setNewAmount(x.newAmount());
        e.setDifference(x.difference());
        e.setSettlementMethod(x.settlementMethod() == null ? null : x.settlementMethod().name());
        e.setPaymentId(x.paymentId());
        e.setSessionId(x.sessionId());
        e.setReason(x.reason());
        e.setCreatedBy(x.createdBy());
        e.setCreatedAt(x.createdAt());
        return e;
    }

    private static OrderItemExchange toDomain(OrderItemExchangeEntity e) {
        return new OrderItemExchange(e.getId(), e.getOrderId(), e.getReturnedOrderItemId(), e.getSku(),
                e.getQuantity(), RefundDestination.valueOf(e.getDestination()), e.getNewOrderItemId(),
                e.getNewSku(), e.getNewQuantity(), e.getReturnedAmount(), e.getNewAmount(), e.getDifference(),
                e.getSettlementMethod() == null ? null : PaymentMethod.valueOf(e.getSettlementMethod()),
                e.getPaymentId(), e.getSessionId(), e.getReason(), e.getCreatedBy(), e.getCreatedAt());
    }
}
