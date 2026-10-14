package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product_category",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_category_name", columnNames = "name"))
public class ProductCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean featured;

    // display_order e não "order": ORDER é palavra reservada em SQL e viraria uma coluna que só
    // funciona entre aspas.
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    // PDV-F045 — V151. Produtos desta categoria são vendidos pela Central de cigarros, não pela grade.
    @Column(nullable = false)
    private boolean cigarette;
}
