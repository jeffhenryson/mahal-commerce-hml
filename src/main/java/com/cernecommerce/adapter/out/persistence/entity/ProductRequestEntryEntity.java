package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Solicitação de um cliente dentro do pedido de cliente (V208). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product_request_entries")
public class ProductRequestEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "max_price", precision = 12, scale = 2)
    private BigDecimal maxPrice;

    @Column(nullable = false, length = 10)
    private String urgency;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "notified_channel", length = 12)
    private String notifiedChannel;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
