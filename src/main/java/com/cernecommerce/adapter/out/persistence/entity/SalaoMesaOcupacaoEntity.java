package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Mesa do QR ocupada por um cliente (V168): enquanto a comanda estiver aberta ou, antes do aceite, a
 * solicitação de origem estiver pendente (V171).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "salao_mesa_ocupacao")
public class SalaoMesaOcupacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "mesa_id", nullable = false)
    private Long mesaId;

    @Column(name = "comanda_id")
    private Long comandaId;

    @Column(name = "solicitacao_id")
    private Long solicitacaoId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 4)
    private String codigo;

    @Column(name = "aberta_em", nullable = false)
    private Instant abertaEm;
}
