package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OpenPackageEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OpenPackageJpaRepository extends JpaRepository<OpenPackageEntity, Long> {

    Optional<OpenPackageEntity> findBySkuAndWarehouseIdAndClosedAtIsNull(String sku, Long warehouseId);

    List<OpenPackageEntity> findByWarehouseIdAndClosedAtIsNullOrderBySkuAsc(Long warehouseId);

    /** Trava a lata em uso até o fim da transação (EST-C025) — ver {@code OpenPackageRepository.findOpenForUpdate}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM OpenPackageEntity p "
            + "WHERE p.sku = :sku AND p.warehouseId = :warehouseId AND p.closedAt IS NULL")
    Optional<OpenPackageEntity> findOpenForUpdate(@Param("sku") String sku, @Param("warehouseId") Long warehouseId);
}
