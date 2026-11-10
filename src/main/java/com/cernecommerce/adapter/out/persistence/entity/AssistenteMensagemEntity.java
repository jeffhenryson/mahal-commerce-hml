package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.domain.model.assistente.PapelMensagem;
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
@Table(name = "assistente_mensagem")
public class AssistenteMensagemEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "conversa_id", nullable = false, length = 36)
    private String conversaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PapelMensagem papel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(columnDefinition = "TEXT")
    private String blocos;

    @Column(columnDefinition = "TEXT")
    private String links;

    @Column(length = 20)
    private String provedor;

    @Column(length = 100)
    private String modelo;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_nota", length = 10)
    private NotaFeedback feedbackNota;

    @Column(name = "feedback_motivo", length = 500)
    private String feedbackMotivo;

    @Column(name = "feedback_em")
    private Instant feedbackEm;
}
