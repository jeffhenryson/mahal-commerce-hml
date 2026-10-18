package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "cash_register_session")
public class CashRegisterSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 80)
    private String operator;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opening_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingAmount;

    /**
     * Depósito de onde sai a mercadoria vendida neste caixa (PDV-C004). Fica na sessão, e não no
     * request de cada venda, para o operador não baixar estoque de depósito alheio.
     */
    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 80)
    private String closedBy;

    @Column(name = "expected_amount", precision = 14, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "counted_amount", precision = 14, scale = 2)
    private BigDecimal countedAmount;

    /** {@code counted − expected}. Negativo é falta no caixa, e é um número legítimo. */
    @Column(name = "difference_amount", precision = 14, scale = 2)
    private BigDecimal differenceAmount;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "closing_notes", length = 500)
    private String closingNotes;

    /** PDV-F056 — troco que fica na gaveta para o próximo turno. */
    @Column(name = "left_in_drawer_amount", precision = 14, scale = 2)
    private BigDecimal leftInDrawerAmount;
}
