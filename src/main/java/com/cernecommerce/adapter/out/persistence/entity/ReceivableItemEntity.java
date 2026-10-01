package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Snapshot de item do pedido marcado (CRM-F010). Tabela {@code receivable_item} (V137). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "receivable_item")
public class ReceivableItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "receivable_id", nullable = false)
    private Long receivableId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(length = 20)
    private String mode;
}
