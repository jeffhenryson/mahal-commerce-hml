package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SupplierProductMappingEntity;
import com.cernecommerce.core.domain.model.compras.SupplierProductMapping;
import com.cernecommerce.core.ports.out.compras.SupplierProductMappingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Repository
@Transactional
public class SupplierProductMappingRepositoryImpl implements SupplierProductMappingRepository {

    private final SupplierProductMappingJpaRepository jpa;

    public SupplierProductMappingRepositoryImpl(SupplierProductMappingJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, SupplierProductMapping> findBySupplierAndCodes(Long supplierId,
            Collection<String> supplierProductCodes) {
        Map<String, SupplierProductMapping> out = new HashMap<>();
        if (supplierId == null || supplierProductCodes == null || supplierProductCodes.isEmpty()) {
            return out;
        }
        for (SupplierProductMappingEntity e : jpa.findBySupplierIdAndSupplierProductCodeIn(supplierId,
                supplierProductCodes)) {
            out.put(e.getSupplierProductCode(), new SupplierProductMapping(e.getSupplierId(),
                    e.getSupplierProductCode(), e.getSku(), e.getUnitsPerPackage()));
        }
        return out;
    }

    @Override
    public void upsert(SupplierProductMapping mapping, String username) {
        SupplierProductMappingEntity e = jpa.findById(new SupplierProductMappingEntity.Key(mapping.supplierId(),
                        mapping.supplierProductCode()))
                .orElseGet(SupplierProductMappingEntity::new);
        e.setSupplierId(mapping.supplierId());
        e.setSupplierProductCode(mapping.supplierProductCode());
        e.setSku(mapping.sku());
        e.setUnitsPerPackage(mapping.unitsPerPackage());
        e.setUpdatedAt(Instant.now());
        e.setUpdatedBy(username);
        jpa.save(e);
    }
}
