package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "assistente_conversa")
public class AssistenteConversaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(length = 120)
    private String titulo;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;
}
