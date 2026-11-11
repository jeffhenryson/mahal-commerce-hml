package com.cernecommerce.core.domain.model.assistente;

import java.time.Instant;

/** Resposta avaliada, com a pergunta que a originou — textos já sem dados pessoais. */
public record FeedbackRegistro(String mensagemId, String pergunta, String resposta, NotaFeedback nota,
        String motivo, String provedor, String modelo, Instant avaliadaEm) { }
