package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Divergência em caixa fechado deixada por uma correção de pagamento (PDV-F030). Tabela V136. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "cash_session_adjustment")
public class CashSessionAdjustmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "correction_id", nullable = false)
    private Long correctionId;

    @Column(nullable = false, length = 30)
    private String method;

    @Column(name = "delta_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal deltaAmount;

    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
