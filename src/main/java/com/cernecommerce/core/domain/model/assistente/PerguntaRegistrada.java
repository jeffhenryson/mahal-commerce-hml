package com.cernecommerce.core.domain.model.assistente;

/**
 * Pergunta do usuário gravada no início do turno. {@code tituloNovo} só vem preenchido quando esta
 * pergunta deu título à conversa (a primeira); nas seguintes é {@code null}.
 */
public record PerguntaRegistrada(Mensagem mensagem, String tituloNovo) { }
