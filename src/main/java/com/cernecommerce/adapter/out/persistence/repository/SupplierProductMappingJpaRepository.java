package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SupplierProductMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SupplierProductMappingJpaRepository
        extends JpaRepository<SupplierProductMappingEntity, SupplierProductMappingEntity.Key> {

    List<SupplierProductMappingEntity> findBySupplierIdAndSupplierProductCodeIn(Long supplierId,
            Collection<String> supplierProductCodes);
}
