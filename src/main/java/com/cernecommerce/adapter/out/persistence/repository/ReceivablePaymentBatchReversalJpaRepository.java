package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReceivablePaymentBatchReversalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReceivablePaymentBatchReversalJpaRepository
        extends JpaRepository<ReceivablePaymentBatchReversalEntity, Long> {

    boolean existsByBatchId(Long batchId);

    List<ReceivablePaymentBatchReversalEntity> findByBatchIdIn(Collection<Long> batchIds);
}
