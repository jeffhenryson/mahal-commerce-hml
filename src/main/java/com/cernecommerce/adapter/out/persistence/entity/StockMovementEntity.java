package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "stock_movement")
public class StockMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "lot_code", length = 50)
    private String lotCode;

    /** Custo unitário da entrada (EST-F007), opcional. {@code null} em SAIDA/AJUSTE. */
    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;

    /** Vínculo opcional com o recebimento que originou a ENTRADA. NULL em movimentação manual. */
    @Column(name = "goods_receipt_id")
    private Long goodsReceiptId;

    /** EST-F039 — depósito do outro lado de uma transferência. NULL em qualquer outro movimento. */
    @Column(name = "counterpart_warehouse_id")
    private Long counterpartWarehouseId;
}
