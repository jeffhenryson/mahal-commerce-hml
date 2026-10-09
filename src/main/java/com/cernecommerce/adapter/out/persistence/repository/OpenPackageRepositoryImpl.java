package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OpenPackageEntity;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.OpenPackageCloseReason;
import com.cernecommerce.core.ports.out.estoque.OpenPackageRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class OpenPackageRepositoryImpl implements OpenPackageRepository {

    private final OpenPackageJpaRepository jpaRepository;

    public OpenPackageRepositoryImpl(OpenPackageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OpenPackage> findOpen(String sku, Long warehouseId) {
        return jpaRepository.findBySkuAndWarehouseIdAndClosedAtIsNull(sku, warehouseId).map(this::toDomain);
    }

    /**
     * Sem {@code readOnly}, de propósito: SELECT FOR UPDATE em transação somente-leitura é
     * contraditório, e alguns drivers o rejeitam. Mesmo motivo de {@code ComandaRepositoryImpl}.
     */
    @Override
    public Optional<OpenPackage> findOpenForUpdate(String sku, Long warehouseId) {
        return jpaRepository.findOpenForUpdate(sku, warehouseId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenPackage> findAllOpen(Long warehouseId) {
        return jpaRepository.findByWarehouseIdAndClosedAtIsNullOrderBySkuAsc(warehouseId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public OpenPackage save(OpenPackage openPackage) {
        OpenPackageEntity entity = openPackage.id() == null
                ? new OpenPackageEntity()
                : jpaRepository.findById(openPackage.id()).orElseGet(OpenPackageEntity::new);
        entity.setId(openPackage.id());
        entity.setSku(openPackage.sku());
        entity.setWarehouseId(openPackage.warehouseId());
        entity.setUses(openPackage.uses());
        entity.setSessionsPerUnit(openPackage.sessionsPerUnit());
        entity.setOpenedAt(openPackage.openedAt());
        entity.setOpenedBy(openPackage.openedBy());
        entity.setClosedAt(openPackage.closedAt());
        entity.setCloseReason(openPackage.closeReason() == null ? null : openPackage.closeReason().name());
        return toDomain(jpaRepository.save(entity));
    }

    private OpenPackage toDomain(OpenPackageEntity e) {
        return new OpenPackage(e.getId(), e.getSku(), e.getWarehouseId(), e.getUses(),
                e.getSessionsPerUnit(), e.getOpenedAt(), e.getOpenedBy(), e.getClosedAt(),
                e.getCloseReason() == null ? null : OpenPackageCloseReason.valueOf(e.getCloseReason()));
    }
}
