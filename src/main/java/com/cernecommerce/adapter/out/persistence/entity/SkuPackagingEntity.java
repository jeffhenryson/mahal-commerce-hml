package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ligação de embalagem (EST-F032, V148). A chave é o filho: um SKU está dentro de no máximo uma
 * embalagem, e a unicidade fica no banco, não só na aplicação.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "sku_packaging")
public class SkuPackagingEntity {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "child_sku", length = 50)
    private String childSku;

    @Column(name = "parent_sku", nullable = false, length = 50)
    private String parentSku;

    @Column(name = "units_per_parent", nullable = false)
    private int unitsPerParent;
}
