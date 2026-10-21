package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Solicitação de sessão feita pelo QR da mesa (V167; escolhas e aparelho em V168). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "salao_solicitacao")
public class SalaoSolicitacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID codigo;

    @Column(name = "mesa_id", nullable = false)
    private Long mesaId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "tier_id")
    private Long tierId;

    @Column(name = "essencia_sku", nullable = false, length = 50)
    private String essenciaSku;

    @Column(name = "essencia_sku_2", length = 50)
    private String essenciaSku2;

    @Column(length = 200)
    private String observacao;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 200)
    private String motivo;

    @Column(name = "comanda_id")
    private Long comandaId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @Column(name = "decidido_por", length = 80)
    private String decididoPor;

    @Column(name = "vaso_grande", nullable = false)
    private boolean vasoGrande;

    @Column(length = 10)
    private String carvao;

    /** Ids dos adicionais separados por vírgula. */
    @Column(name = "adicional_ids", length = 200)
    private String adicionalIds;

    @Column(name = "pagar_no_final", nullable = false)
    private boolean pagarNoFinal;

    @Column(name = "device_token", length = 64)
    private String deviceToken;

    /** Proporção da mescla (V169); nula com um sabor só. */
    @Column(length = 20)
    private String proporcao;
}
