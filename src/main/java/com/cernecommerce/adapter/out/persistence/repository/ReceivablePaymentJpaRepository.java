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

    List<ReceivablePaymentEntity> findByBatchIdOrderByIdAsc(Long batchId);

    /** V173 — lote estornado não é dinheiro recebido: fica fora do esperado e de payment-totals. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM ReceivablePaymentEntity p
            WHERE p.cashSessionId = :sessionId AND p.method = :method
              AND NOT EXISTS (SELECT 1 FROM ReceivablePaymentBatchReversalEntity r WHERE r.batchId = p.batchId)
            """)
    BigDecimal sumBySessionAndMethod(@Param("sessionId") Long sessionId, @Param("method") String method);

    /** PDV-C051 — pares {@code [method, soma]} das quitações recebidas na sessão, sem as estornadas (V173). */
    @Query("""
            SELECT p.method, SUM(p.amount) FROM ReceivablePaymentEntity p
            WHERE p.cashSessionId = :sessionId
              AND NOT EXISTS (SELECT 1 FROM ReceivablePaymentBatchReversalEntity r WHERE r.batchId = p.batchId)
            GROUP BY p.method
            """)
    List<Object[]> sumBySessionGroupByMethod(@Param("sessionId") Long sessionId);

    /** V173 — o que foi lançado na sessão e depois estornado, todos os métodos. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM ReceivablePaymentEntity p
            WHERE p.cashSessionId = :sessionId
              AND EXISTS (SELECT 1 FROM ReceivablePaymentBatchReversalEntity r WHERE r.batchId = p.batchId)
            """)
    BigDecimal sumReversedBySession(@Param("sessionId") Long sessionId);
}
