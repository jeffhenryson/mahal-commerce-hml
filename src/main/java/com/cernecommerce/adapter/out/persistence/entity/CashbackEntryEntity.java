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
@Table(name = "cashback_entry")
public class CashbackEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "reverses_entry_id")
    private Long reversesEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** V206 — motivo de um lançamento fora do fluxo normal (ex.: CUSTOMER_DELETED). */
    @Column(length = 40)
    private String reason;
}
