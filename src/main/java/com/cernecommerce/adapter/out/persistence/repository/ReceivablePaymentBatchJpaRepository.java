package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReceivablePaymentBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceivablePaymentBatchJpaRepository extends JpaRepository<ReceivablePaymentBatchEntity, Long> {

    List<ReceivablePaymentBatchEntity> findByCashSessionId(Long cashSessionId);
}
