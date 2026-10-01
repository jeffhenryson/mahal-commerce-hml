package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReceivablePaymentBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivablePaymentBatchJpaRepository extends JpaRepository<ReceivablePaymentBatchEntity, Long> {
}
