package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Linha de pagamento de um pedido (PDV-F006). Tabela {@code order_payment} (V68).
 *
 * <p>{@code orderId} é um campo simples, sem {@code @ManyToOne} para {@link OrderEntity} — o
 * domínio {@code Order} deliberadamente não carrega pagamento como agregado (ver o javadoc de
 * {@code Order}), e a entidade espelha essa decisão.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "order_payment")
public class OrderPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 30)
    private String method;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    private Integer installments;

    @Column(name = "gateway_ref", length = 120)
    private String gatewayRef;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // PDV-F025 — maquininha ou link, e a operadora. Nulos em DINHEIRO e em pagamento anterior à V134.
    @Column(length = 20)
    private String channel;

    @Column(length = 20)
    private String provider;

    // PDV-F030 — lastro da correção de forma de pagamento (V136).
    @Column(name = "correction_id")
    private Long correctionId;

    @Column(name = "origin_correction_id")
    private Long originCorrectionId;

    @Column(name = "corrected_at")
    private Instant correctedAt;

    @Column(name = "corrected_by", length = 80)
    private String correctedBy;

    // CRM-F010 — vencimento da linha MARCADO (V137).
    @Column(name = "due_date")
    private java.time.LocalDate dueDate;
}
