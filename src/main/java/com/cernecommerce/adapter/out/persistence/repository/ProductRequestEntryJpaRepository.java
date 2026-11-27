package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductRequestEntryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRequestEntryJpaRepository extends JpaRepository<ProductRequestEntryEntity, Long> {

    List<ProductRequestEntryEntity> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);

    /** requestId, entriesCount, totalQuantity, maxUrgency (1..3), earliestNeededBy, maxPriceMin, maxPriceMax, pending. */
    @Query("""
            SELECT e.requestId, COUNT(e.id), COALESCE(SUM(e.quantity), 0),
                   MAX(CASE e.urgency WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END),
                   MIN(e.neededBy), MIN(e.maxPrice), MAX(e.maxPrice),
                   SUM(CASE WHEN e.notifiedAt IS NULL THEN 1 ELSE 0 END)
            FROM ProductRequestEntryEntity e
            WHERE e.requestId IN :ids AND e.status = 'ACTIVE'
            GROUP BY e.requestId
            """)
    List<Object[]> statsFor(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT e.requestId FROM ProductRequestEntryEntity e, ProductRequestEntity r
            WHERE e.requestId = r.id AND e.status = 'ACTIVE' AND r.status IN ('PENDING', 'ORDERED', 'ARRIVED')
            GROUP BY e.requestId
            ORDER BY COUNT(e.id) DESC, e.requestId ASC
            """)
    List<Long> topRequestedIds(Pageable pageable);
}
