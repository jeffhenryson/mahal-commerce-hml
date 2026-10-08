package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Limite de crédito individual do "Marcar" (CRM-F010), um por cliente e canal. Tabela
 * {@code customer_credit_limit} (V137; canal na V142): {@code channel} é TOTAL, BALCAO ou MESA.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "customer_credit_limit")
@IdClass(CustomerCreditLimitEntity.Key.class)
public class CustomerCreditLimitEntity {

    public static final String TOTAL = "TOTAL";

    @Id
    @Column(name = "customer_id")
    @EqualsAndHashCode.Include
    private Long customerId;

    @Id
    @Column(name = "channel", length = 10)
    @EqualsAndHashCode.Include
    private String channel;

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "updated_by", nullable = false, length = 80)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long customerId;
        private String channel;
    }
}
