package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.WarehouseEntity;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WarehouseJpaRepository extends JpaRepository<WarehouseEntity, Long> {

    Optional<WarehouseEntity> findByCode(String code);

    // Ordena por id (BIGSERIAL monotônico) para a paginação ser estável: chave de ordenação
    // não-única deixa o banco livre para repetir ou pular linha entre páginas (EST-C012).
    @Query("SELECT w FROM WarehouseEntity w ORDER BY w.id")
    Page<WarehouseEntity> findAllOrderById(Pageable pageable);

    Optional<WarehouseEntity> findFirstByTypeAndActiveTrueOrderByIdAsc(WarehouseType type);
}
