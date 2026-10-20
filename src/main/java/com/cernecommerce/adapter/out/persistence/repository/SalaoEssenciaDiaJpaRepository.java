package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoEssenciaDiaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SalaoEssenciaDiaJpaRepository
        extends JpaRepository<SalaoEssenciaDiaEntity, SalaoEssenciaDiaEntity.Id> {

    List<SalaoEssenciaDiaEntity> findByData(LocalDate data);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SalaoEssenciaDiaEntity e WHERE e.data = :data")
    void deleteByData(@Param("data") LocalDate data);
}
