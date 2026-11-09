package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Adicional pago do cardápio de sessão (PDV-F024, V133). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "session_addon")
public class SessionAddonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false)
    private boolean ativo;

    // PDV-F061 — pode ser incluído com a sessão já na mesa (V191).
    @Column(name = "permite_pos_entrega", nullable = false)
    private boolean permitePosEntrega;
}
