package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderPaymentCorrectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderPaymentCorrectionJpaRepository extends JpaRepository<OrderPaymentCorrectionEntity, Long> {

    List<OrderPaymentCorrectionEntity> findByOrderIdOrderByIdAsc(Long orderId);
}
