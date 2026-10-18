package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderItemExchangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemExchangeJpaRepository extends JpaRepository<OrderItemExchangeEntity, Long> {

    List<OrderItemExchangeEntity> findByOrderIdOrderByIdAsc(Long orderId);
}
