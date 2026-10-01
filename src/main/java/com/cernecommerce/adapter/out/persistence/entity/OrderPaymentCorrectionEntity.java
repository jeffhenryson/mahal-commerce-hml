package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Correção da forma de pagamento de um pedido (PDV-F030). Tabela {@code order_payment_correction} (V136). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "order_payment_correction")
public class OrderPaymentCorrectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "corrected_by", nullable = false, length = 80)
    private String correctedBy;

    @Column(name = "corrected_at", nullable = false)
    private Instant correctedAt;

    @Column(name = "cash_session_id", nullable = false)
    private Long cashSessionId;

    @Column(name = "session_was_closed", nullable = false)
    private boolean sessionWasClosed;
}
