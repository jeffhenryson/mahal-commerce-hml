package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Chamado feito pelo QR, na comanda da mesa ou na aberta pelo PDV (V169). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "salao_chamado")
public class SalaoChamadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "comanda_id", nullable = false)
    private Long comandaId;

    @Column(name = "mesa_id")
    private Long mesaId;

    @Column(name = "ocupacao_id")
    private Long ocupacaoId;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "forma_pagamento", length = 20)
    private String formaPagamento;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atendido_em")
    private Instant atendidoEm;

    @Column(name = "atendido_por", length = 80)
    private String atendidoPor;
}
