package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReceivablePaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface ReceivablePaymentJpaRepository extends JpaRepository<ReceivablePaymentEntity, Long> {

    List<ReceivablePaymentEntity> findByReceivableIdInOrderByIdAsc(Collection<Long> receivableIds);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM ReceivablePaymentEntity p
            WHERE p.cashSessionId = :sessionId AND p.method = :method
            """)
    BigDecimal sumBySessionAndMethod(@Param("sessionId") Long sessionId, @Param("method") String method);
}
