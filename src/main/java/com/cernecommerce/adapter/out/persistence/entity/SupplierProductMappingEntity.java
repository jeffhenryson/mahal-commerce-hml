package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@IdClass(SupplierProductMappingEntity.Key.class)
@Table(name = "supplier_product_mapping")
public class SupplierProductMappingEntity {

    @Id
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Id
    @Column(name = "supplier_product_code", nullable = false, length = 60)
    private String supplierProductCode;

    @Column(length = 50)
    private String sku;

    @Column(name = "units_per_package", nullable = false)
    private int unitsPerPackage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 80)
    private String updatedBy;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long supplierId;
        private String supplierProductCode;

        public Key(Long supplierId, String supplierProductCode) {
            this.supplierId = supplierId;
            this.supplierProductCode = supplierProductCode;
        }
    }
}
