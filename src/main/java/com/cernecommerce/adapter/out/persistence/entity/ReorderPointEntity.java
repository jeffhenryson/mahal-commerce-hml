package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "stock_reorder_point", uniqueConstraints = @UniqueConstraint(name = "uk_stock_reorder_point_sku_warehouse", columnNames = {"sku", "warehouse_id"}))
public class ReorderPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "min_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal minQuantity;

    /** Corte do nível crítico; nulo = padrão (metade do mínimo). */
    @Column(name = "critical_quantity", precision = 14, scale = 3)
    private BigDecimal criticalQuantity;
}
