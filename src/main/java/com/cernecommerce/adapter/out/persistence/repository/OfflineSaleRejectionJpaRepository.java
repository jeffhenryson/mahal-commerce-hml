package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OfflineSaleRejectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfflineSaleRejectionJpaRepository extends JpaRepository<OfflineSaleRejectionEntity, Long> {

    Optional<OfflineSaleRejectionEntity> findByClientSaleId(String clientSaleId);

    List<OfflineSaleRejectionEntity> findBySessionIdOrderByIdDesc(Long sessionId);

    @Query("SELECT r.id FROM OfflineSaleRejectionEntity r WHERE r.sessionId = :sessionId AND r.resolvedAt IS NULL "
            + "ORDER BY r.id ASC")
    List<Long> findPendingIds(@Param("sessionId") Long sessionId);
}
