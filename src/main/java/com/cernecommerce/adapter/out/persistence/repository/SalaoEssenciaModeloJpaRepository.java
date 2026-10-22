package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoEssenciaModeloEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalaoEssenciaModeloJpaRepository
        extends JpaRepository<SalaoEssenciaModeloEntity, SalaoEssenciaModeloEntity.Id> {

    List<SalaoEssenciaModeloEntity> findByDiaSemana(Integer diaSemana);

    List<SalaoEssenciaModeloEntity> findAllByOrderByDiaSemanaAsc();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SalaoEssenciaModeloEntity e WHERE e.diaSemana = :dia")
    void deleteByDiaSemana(@Param("dia") Integer dia);
}
