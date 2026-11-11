package com.cernecommerce.adapter.in.dtos.response;

import java.time.Instant;

/** Resposta avaliada com a pergunta que a originou ({@code GET /assistente/feedbacks}); sem o usuário. */
public record AssistenteFeedbackRegistroDTO(String mensagemId, String pergunta, String resposta, String nota,
        String motivo, String provedor, String modelo, Instant avaliadaEm) { }
