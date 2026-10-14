package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OpenPackageEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * EST-F037 — [sku, quantas, soma dos usos] das latas fechadas no período, com ao menos um uso.
     * Limites sempre preenchidos (o service troca nulo por EPOCH/teto): parâmetro nulo sem tipo
     * vira bytea no Postgres (EST-C024).
     */
    @Query("SELECT p.sku, COUNT(p), SUM(p.uses) FROM OpenPackageEntity p "
            + "WHERE p.warehouseId = :warehouseId AND p.closedAt IS NOT NULL AND p.uses > 0 "
            + "AND p.closedAt >= :from AND p.closedAt <= :to "
            + "GROUP BY p.sku ORDER BY p.sku")
    List<Object[]> closedYield(@Param("warehouseId") Long warehouseId, @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);

    /** EST-F038 — {@link #closedYield} sem o filtro de depósito (rendimento do Salão). */
    @Query("SELECT p.sku, COUNT(p), SUM(p.uses) FROM OpenPackageEntity p "
            + "WHERE p.closedAt IS NOT NULL AND p.uses > 0 "
            + "AND p.closedAt >= :from AND p.closedAt <= :to "
            + "GROUP BY p.sku ORDER BY p.sku")
    List<Object[]> closedYieldAllWarehouses(@Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);

    /**
     * EST-F038 — fecha as latas em uso que têm outra mais nova do mesmo SKU em uso (em qualquer
     * depósito). Desempate pelo id, para duas abertas no mesmo instante não fecharem as duas.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE OpenPackageEntity p SET p.closedAt = :closedAt, p.closeReason = 'REPLACED' "
            + "WHERE p.closedAt IS NULL AND EXISTS (SELECT 1 FROM OpenPackageEntity o "
            + "WHERE o.closedAt IS NULL AND o.sku = p.sku AND o.id <> p.id "
            + "AND (o.openedAt > p.openedAt OR (o.openedAt = p.openedAt AND o.id > p.id)))")
    int closeOlderDuplicatesOpen(@Param("closedAt") java.time.Instant closedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OpenPackageEntity p SET p.warehouseId = :target "
            + "WHERE p.closedAt IS NULL AND p.warehouseId <> :target")
    int moveAllOpenTo(@Param("target") Long target);
}
