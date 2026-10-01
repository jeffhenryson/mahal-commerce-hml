package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Abatimento de um marcado (CRM-F010). Tabela {@code receivable_payment} (V137). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "receivable_payment")
public class ReceivablePaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "receivable_id", nullable = false)
    private Long receivableId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String method;

    private Integer installments;

    @Column(length = 20)
    private String channel;

    @Column(length = 20)
    private String provider;

    @Column(name = "cash_session_id", nullable = false)
    private Long cashSessionId;

    @Column(name = "received_by", nullable = false, length = 80)
    private String receivedBy;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
