package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.crm.CustomerStage;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "customers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_customers_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_customers_cpf", columnNames = "cpf")
}, indexes = @Index(name = "idx_customers_phone_normalized", columnList = "phone_normalized"))
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 255)
    private String nome;

    /** Opcional desde CRM-C005 — cpf ou email sozinhos já identificam o cliente. */
    @Column(length = 30)
    private String contato;

    /** {@code contato} só com dígitos e sem DDI 55 (CRM-C007) — base do lookup e da checagem de duplicidade. */
    @Column(name = "phone_normalized", length = 30)
    private String phoneNormalized;

    /** Opcional desde CRM-C005 — cliente "leve" pode ser identificado só por cpf/contato. */
    @Column(length = 255)
    private String email;

    /** Identificador OFICIAL do cadastro (CRM-C005) — único quando presente. */
    @Column(length = 11)
    private String cpf;

    @Column(length = 100)
    private String origem;

    @Column(name = "cadastrado_em", nullable = false)
    private Instant cadastradoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStage estagio;

    /** CRM-F012 — opcional; base da automação de aniversário. */
    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;
}
