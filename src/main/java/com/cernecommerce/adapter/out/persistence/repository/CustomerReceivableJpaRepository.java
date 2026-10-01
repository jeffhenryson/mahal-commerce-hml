package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerReceivableEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerReceivableJpaRepository extends JpaRepository<CustomerReceivableEntity, Long>,
        JpaSpecificationExecutor<CustomerReceivableEntity> {

    Optional<CustomerReceivableEntity> findByOrderId(Long orderId);

    List<CustomerReceivableEntity> findByOrderIdIn(Collection<Long> orderIds);

    List<CustomerReceivableEntity> findByCustomerIdOrderByIdDesc(Long customerId);

    /** Trava os em aberto do cliente: duas quitações simultâneas não abatem o mesmo saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM CustomerReceivableEntity r
            WHERE r.customerId = :customerId AND r.status IN ('ABERTO', 'PARCIAL', 'VENCIDO')
            ORDER BY r.dueDate ASC, r.createdAt ASC, r.id ASC
            """)
    List<CustomerReceivableEntity> findOpenByCustomerIdForUpdate(@Param("customerId") Long customerId);

    @Query("""
            SELECT COALESCE(SUM(r.amount - r.amountPaid), 0) FROM CustomerReceivableEntity r
            WHERE r.customerId = :customerId AND r.status IN ('ABERTO', 'PARCIAL', 'VENCIDO')
            """)
    BigDecimal sumOpenBalance(@Param("customerId") Long customerId);

    @Query("""
            SELECT COALESCE(SUM(r.amount - r.amountPaid), 0) FROM CustomerReceivableEntity r
            WHERE r.customerId = :customerId AND r.status IN ('ABERTO', 'PARCIAL', 'VENCIDO')
              AND r.dueDate < :today
            """)
    BigDecimal sumOverdueBalance(@Param("customerId") Long customerId, @Param("today") LocalDate today);

    @Query("""
            SELECT r.customerId,
                   SUM(r.amount - r.amountPaid),
                   SUM(CASE WHEN r.dueDate < :today AND r.status IN ('ABERTO', 'PARCIAL', 'VENCIDO')
                            THEN r.amount - r.amountPaid ELSE 0 END),
                   MIN(r.dueDate),
                   COUNT(r)
            FROM CustomerReceivableEntity r
            WHERE r.status IN :statuses
            GROUP BY r.customerId
            """)
    List<Object[]> summarizeByCustomer(@Param("statuses") Collection<String> statuses,
            @Param("today") LocalDate today);

    @Modifying
    @Query("""
            UPDATE CustomerReceivableEntity r SET r.status = 'VENCIDO', r.version = r.version + 1
            WHERE r.status IN ('ABERTO', 'PARCIAL') AND r.dueDate < :today
            """)
    int markOverdue(@Param("today") LocalDate today);
}
