package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.NfeImportEntity;
import com.cernecommerce.adapter.out.persistence.entity.NfeImportLineEntity;
import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.domain.model.compras.NfeImportStatus;
import com.cernecommerce.core.ports.out.compras.NfeImportRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class NfeImportRepositoryImpl implements NfeImportRepository {

    private final NfeImportJpaRepository nfeImportJpaRepository;

    public NfeImportRepositoryImpl(NfeImportJpaRepository nfeImportJpaRepository) {
        this.nfeImportJpaRepository = nfeImportJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NfeImport> findById(Long id) {
        return nfeImportJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public NfeImport save(NfeImport nfeImport) {
        NfeImportEntity entity = nfeImport.id() == null
                ? new NfeImportEntity()
                : nfeImportJpaRepository.findById(nfeImport.id()).orElseGet(NfeImportEntity::new);
        entity.setId(nfeImport.id());
        entity.setSupplierId(nfeImport.supplierId());
        entity.setEmitterCnpj(nfeImport.emitterCnpj());
        entity.setWarehouseCode(nfeImport.warehouseCode());
        entity.setFileReference(nfeImport.fileReference());
        entity.setStatus(nfeImport.status().name());
        entity.setGoodsReceiptId(nfeImport.goodsReceiptId());
        entity.setUploadedBy(nfeImport.uploadedBy());
        entity.setUploadedAt(nfeImport.uploadedAt());
        entity.setConfirmedAt(nfeImport.confirmedAt());
        entity.setAccessKey(nfeImport.accessKey());

        // Linhas reescritas por inteiro — mesmo padrão de OrderRepositoryImpl/ComandaRepositoryImpl.
        entity.getLines().clear();
        for (NfeImportLine line : nfeImport.lines()) {
            NfeImportLineEntity lineEntity = new NfeImportLineEntity();
            lineEntity.setNfeImport(entity);
            lineEntity.setItemNumber(line.itemNumber());
            lineEntity.setSupplierProductCode(line.supplierProductCode());
            lineEntity.setEan(line.ean());
            lineEntity.setDescription(line.description());
            lineEntity.setQuantity(line.quantity());
            lineEntity.setUnitPrice(line.unitPrice());
            lineEntity.setLotCode(line.lotCode());
            lineEntity.setExpiryDate(line.expiryDate());
            lineEntity.setMatchStatus(line.matchStatus().name());
            lineEntity.setMatchedSku(line.matchedSku());
            lineEntity.setUnit(line.unit());
            lineEntity.setTaxUnit(line.taxUnit());
            lineEntity.setTaxQuantity(line.taxQuantity());
            lineEntity.setUnitsPerPackage(line.unitsPerPackage());
            entity.getLines().add(lineEntity);
        }
        return toDomain(nfeImportJpaRepository.save(entity));
    }

    private NfeImport toDomain(NfeImportEntity e) {
        return NfeImport.of(e.getId(), e.getSupplierId(), e.getEmitterCnpj(), e.getWarehouseCode(),
                e.getFileReference(), NfeImportStatus.valueOf(e.getStatus()), e.getGoodsReceiptId(),
                e.getLines().stream().map(this::toDomain).toList(), e.getUploadedBy(), e.getUploadedAt(),
                e.getConfirmedAt(), e.getAccessKey());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NfeImport> findConfirmedByAccessKey(String accessKey) {
        if (accessKey == null) {
            return Optional.empty();
        }
        return nfeImportJpaRepository.findFirstByAccessKeyAndStatus(accessKey, NfeImportStatus.CONFIRMED.name())
                .map(this::toDomain);
    }

    private NfeImportLine toDomain(NfeImportLineEntity e) {
        return NfeImportLine.of(e.getId(), e.getItemNumber(), e.getSupplierProductCode(), e.getEan(),
                e.getDescription(), e.getQuantity(), e.getUnitPrice(), e.getLotCode(), e.getExpiryDate(),
                NfeImportLine.MatchStatus.valueOf(e.getMatchStatus()), e.getMatchedSku(), e.getUnit(),
                e.getTaxUnit(), e.getTaxQuantity(), e.getUnitsPerPackage());
    }
}
