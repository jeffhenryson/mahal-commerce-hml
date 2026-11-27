package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** Pedido de cliente (V208). Solicitações e histórico ficam em tabelas próprias, ligadas por {@code request_id}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product_requests")
public class ProductRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 160)
    private String description;

    @Column(length = 80)
    private String brand;

    @Column(length = 80)
    private String variation;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_other", length = 80)
    private String categoryOther;

    @Column(name = "normalized_key", nullable = false, length = 400)
    private String normalizedKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "linked_sku", length = 100)
    private String linkedSku;

    @Column(name = "expected_at")
    private LocalDate expectedAt;

    @Column(name = "supplier_note", length = 500)
    private String supplierNote;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
