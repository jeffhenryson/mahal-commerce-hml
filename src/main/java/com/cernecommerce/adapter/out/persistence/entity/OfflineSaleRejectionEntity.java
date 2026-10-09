package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Venda offline recusada esperando revisão (PDV-F043, V150). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "offline_sale_rejection")
public class OfflineSaleRejectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "client_sale_id", nullable = false, length = 36, unique = true)
    private String clientSaleId;

    @Column(name = "client_sold_at")
    private Instant clientSoldAt;

    @Column(name = "customer_id")
    private Long customerId;

    /** Itens e pagamentos como chegaram, em JSON — é o que o reenvio usa. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_code", nullable = false, length = 60)
    private String errorCode;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    // Enum como String, convenção do projeto.
    @Column(length = 20)
    private String resolution;

    @Column(name = "resolution_note", length = 255)
    private String resolutionNote;

    @Column(name = "order_id")
    private Long orderId;
}
