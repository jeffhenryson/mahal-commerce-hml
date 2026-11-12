package com.cernecommerce.adapter.in.dtos.response;

/** Avaliação do usuário numa resposta do assistente. {@code nota}: POSITIVO ou NEGATIVO. */
public record AssistenteFeedbackDTO(String nota, String motivo) { }
