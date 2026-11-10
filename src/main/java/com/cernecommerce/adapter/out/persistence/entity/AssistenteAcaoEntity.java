package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.assistente.EstadoAcao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assistente_acao")
public class AssistenteAcaoEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "conversa_id", nullable = false, length = 36, updatable = false)
    private String conversaId;

    @Column(name = "mensagem_id", nullable = false, length = 36, updatable = false)
    private String mensagemId;

    @Column(nullable = false, length = 80, updatable = false)
    private String username;

    @Column(nullable = false, length = 40, updatable = false)
    private String tipo;

    /** Imutável: o confirmar executa exatamente isto. */
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String parametros;

    @Column(nullable = false, length = 200, updatable = false)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String campos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAcao estado;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private Instant expiraEm;

    @Column(columnDefinition = "TEXT")
    private String resultado;

    @Column(columnDefinition = "TEXT")
    private String links;

    @Column(name = "executada_em")
    private Instant executadaEm;
}
