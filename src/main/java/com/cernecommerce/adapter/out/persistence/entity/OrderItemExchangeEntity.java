package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Troca de item do pedido (PED-F006). Tabela {@code order_item_exchange} (V164). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "order_item_exchange")
public class OrderItemExchangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "returned_order_item_id", nullable = false)
    private Long returnedOrderItemId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 20)
    private String destination;

    @Column(name = "new_order_item_id", nullable = false)
    private Long newOrderItemId;

    @Column(name = "new_sku", nullable = false, length = 100)
    private String newSku;

    @Column(name = "new_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal newQuantity;

    @Column(name = "returned_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal returnedAmount;

    @Column(name = "new_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal newAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal difference;

    @Column(name = "settlement_method", length = 20)
    private String settlementMethod;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
