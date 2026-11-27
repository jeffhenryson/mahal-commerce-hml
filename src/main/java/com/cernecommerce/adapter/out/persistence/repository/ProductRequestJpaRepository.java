package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRequestJpaRepository extends JpaRepository<ProductRequestEntity, Long> {

    List<ProductRequestEntity> findByStatusIn(Collection<String> statuses);

    List<ProductRequestEntity> findByStatusAndLinkedSkuIn(String status, Collection<String> skus);

    @Query("SELECT r.status, COUNT(r) FROM ProductRequestEntity r GROUP BY r.status")
    List<Object[]> countGroupedByStatus();

    @Query("""
            SELECT COUNT(DISTINCT r.id) FROM ProductRequestEntity r, ProductRequestEntryEntity e
            WHERE e.requestId = r.id AND r.status = 'ARRIVED' AND e.status = 'ACTIVE' AND e.notifiedAt IS NULL
            """)
    long countArrivedAwaitingNotification();

    @Query("""
            SELECT r FROM ProductRequestEntity r
            WHERE r.id IN (SELECT e.requestId FROM ProductRequestEntryEntity e WHERE e.customerId = :customerId)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<ProductRequestEntity> findByCustomer(@Param("customerId") Long customerId);
}
