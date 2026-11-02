package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Item de um {@link NfeImportEntity} (EST-F005). Tabela {@code nfe_import_line}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "nfe_import_line")
public class NfeImportLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nfe_import_id", nullable = false, foreignKey = @ForeignKey(name = "fk_nfe_import_line_nfe_import"))
    @ToString.Exclude
    private NfeImportEntity nfeImport;

    @Column(name = "item_number", nullable = false)
    private int itemNumber;

    @Column(name = "supplier_product_code", nullable = false, length = 60)
    private String supplierProductCode;

    @Column(length = 20)
    private String ean;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "lot_code", length = 50)
    private String lotCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "match_status", nullable = false, length = 20)
    private String matchStatus;

    @Column(name = "matched_sku", length = 50)
    private String matchedSku;

    /** V182 — uCom. */
    @Column(length = 10)
    private String unit;

    /** V182 — uTrib. */
    @Column(name = "tax_unit", length = 10)
    private String taxUnit;

    /** V182 — qTrib. */
    @Column(name = "tax_quantity", precision = 14, scale = 4)
    private BigDecimal taxQuantity;

    /** V182 — fator de embalagem usado (ou sugerido) para a entrada. */
    @Column(name = "units_per_package")
    private Integer unitsPerPackage;
}
