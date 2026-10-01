package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Limite de crédito individual do "Marcar" (CRM-F010). Tabela {@code customer_credit_limit} (V137). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "customer_credit_limit")
public class CustomerCreditLimitEntity {

    @Id
    @Column(name = "customer_id")
    @EqualsAndHashCode.Include
    private Long customerId;

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "updated_by", nullable = false, length = 80)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
