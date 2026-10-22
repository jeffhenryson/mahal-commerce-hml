package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoMesaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalaoMesaJpaRepository extends JpaRepository<SalaoMesaEntity, Long> {

    List<SalaoMesaEntity> findAllByOrderByNomeAsc();

    Optional<SalaoMesaEntity> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM SalaoMesaEntity m WHERE m.id = :id")
    Optional<SalaoMesaEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COUNT(m) > 0 FROM SalaoMesaEntity m
            WHERE LOWER(m.nome) = LOWER(:nome) AND (:excetoId IS NULL OR m.id <> :excetoId)
            """)
    boolean existsByNome(@Param("nome") String nome, @Param("excetoId") Long excetoId);
}
