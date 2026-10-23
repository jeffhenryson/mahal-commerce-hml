package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Estorno de recebimento de marcado (CRM-F010). Tabela {@code receivable_payment_batch_reversal} (V173). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "receivable_payment_batch_reversal")
public class ReceivablePaymentBatchReversalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "batch_id", nullable = false, unique = true)
    private Long batchId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "reversed_by", nullable = false, length = 80)
    private String reversedBy;

    @Column(name = "reversed_at", nullable = false)
    private Instant reversedAt;

    @Column(name = "session_open_at_reversal", nullable = false)
    private boolean sessionOpenAtReversal;
}
