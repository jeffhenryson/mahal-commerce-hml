package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderItemRefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRefundJpaRepository extends JpaRepository<OrderItemRefundEntity, Long> {

    List<OrderItemRefundEntity> findByOrderIdOrderByIdAsc(Long orderId);

    /** Pares (orderItemId, unidades estornadas) do pedido. */
    @Query("""
            SELECT r.orderItemId, SUM(r.quantity)
            FROM OrderItemRefundEntity r
            WHERE r.orderId = :orderId
            GROUP BY r.orderItemId
            """)
    List<Object[]> sumQuantityByOrderItem(@Param("orderId") Long orderId);
}
