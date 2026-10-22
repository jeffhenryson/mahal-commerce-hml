package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoChamadoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalaoChamadoJpaRepository extends JpaRepository<SalaoChamadoEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SalaoChamadoEntity c WHERE c.id = :id")
    Optional<SalaoChamadoEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByComandaIdAndTipoAndStatusAndCriadoEmAfter(Long comandaId, String tipo, String status,
            Instant desde);

    List<SalaoChamadoEntity> findByComandaIdOrderByCriadoEmDesc(Long comandaId);

    List<SalaoChamadoEntity> findByComandaIdInOrderByCriadoEmDesc(Collection<Long> comandaIds);

    List<SalaoChamadoEntity> findByStatusAndCriadoEmAfterOrderByCriadoEmAsc(String status, Instant desde);
}
