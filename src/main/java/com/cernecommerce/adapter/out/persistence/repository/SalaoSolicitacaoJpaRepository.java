package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoSolicitacaoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SalaoSolicitacaoJpaRepository extends JpaRepository<SalaoSolicitacaoEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SalaoSolicitacaoEntity s WHERE s.id = :id")
    Optional<SalaoSolicitacaoEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<SalaoSolicitacaoEntity> findByCodigo(UUID codigo);

    boolean existsByMesaIdAndCustomerIdAndStatusAndCriadoEmAfter(Long mesaId, Long customerId, String status,
            Instant desde);

    boolean existsByMesaIdAndDeviceTokenAndStatusAndCriadoEmAfter(Long mesaId, String deviceToken, String status,
            Instant desde);

    Optional<SalaoSolicitacaoEntity> findFirstByMesaIdAndStatusOrderByDecididoEmDesc(Long mesaId, String status);

    Page<SalaoSolicitacaoEntity> findByStatusOrderByCriadoEmDesc(String status, Pageable pageable);

    Page<SalaoSolicitacaoEntity> findByStatusAndCriadoEmAfterOrderByCriadoEmDesc(String status, Instant desde,
            Pageable pageable);
}
