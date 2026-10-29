package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Atendimento no caixa de um pedido do app (ECM-F010, V174). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "pedido_app_atendimento")
public class PedidoAppAtendimentoEntity {

    @Id
    @Column(name = "order_id")
    @EqualsAndHashCode.Include
    private Long orderId;

    // Enums como String, convenção do projeto.
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "preferencia_pagamento", nullable = false, length = 20)
    private String preferenciaPagamento;

    @Column(length = 500)
    private String observacao;

    @Column(nullable = false)
    private Instant prazo;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "atendido_por", length = 100)
    private String atendidoPor;

    @Column(length = 500)
    private String motivo;

    /** Código da recusa pela loja (ECM-F012, V176). */
    @Column(name = "motivo_codigo", length = 30)
    private String motivoCodigo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "aceito_em")
    private Instant aceitoEm;

    @Column(name = "pronto_em")
    private Instant prontoEm;

    @Column(name = "saiu_em")
    private Instant saiuEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;
}
