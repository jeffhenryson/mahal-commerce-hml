package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Recebimento de marcado no balcão (CRM-F010). Tabela {@code receivable_payment_batch} (V137). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "receivable_payment_batch")
public class ReceivablePaymentBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "cash_session_id", nullable = false)
    private Long cashSessionId;

    @Column(name = "change_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "received_by", nullable = false, length = 80)
    private String receivedBy;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
