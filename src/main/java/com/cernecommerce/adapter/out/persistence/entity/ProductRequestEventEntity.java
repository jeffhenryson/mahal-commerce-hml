package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Histórico do pedido de cliente (V208). {@code payload} é JSON em texto. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product_request_events")
public class ProductRequestEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "entry_id")
    private Long entryId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 255)
    private String actor;

    @Column(name = "at", nullable = false)
    private Instant occurredAt;
}
