package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoEssenciaPerfilEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalaoEssenciaPerfilJpaRepository
        extends JpaRepository<SalaoEssenciaPerfilEntity, SalaoEssenciaPerfilEntity.Id> {

    List<SalaoEssenciaPerfilEntity> findBySkuIn(Collection<String> skus);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SalaoEssenciaPerfilEntity e WHERE e.sku = :sku")
    void deleteBySku(@Param("sku") String sku);
}
