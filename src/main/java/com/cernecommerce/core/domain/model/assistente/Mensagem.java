package com.cernecommerce.core.domain.model.assistente;

import java.time.Instant;

/**
 * Mensagem gravada. {@code texto} já passou pela remoção de dados pessoais. {@code blocosJson} e
 * {@code linksJson} são o que o stream emitiu, serializados pelo adaptador de entrada.
 * {@code provedor}/{@code modelo}: quem respondeu (só na do assistente; a reserva pode ter entrado).
 * {@code feedback*}: avaliação do usuário, só na do assistente; {@code null} quando não avaliada.
 */
public record Mensagem(String id, String conversaId, PapelMensagem papel, String texto, String blocosJson,
        String linksJson, String provedor, String modelo, Instant criadaEm, NotaFeedback feedbackNota,
        String feedbackMotivo, Instant feedbackEm) {

    /** Mensagem nova, ainda sem avaliação. */
    public Mensagem(String id, String conversaId, PapelMensagem papel, String texto, String blocosJson,
            String linksJson, String provedor, String modelo, Instant criadaEm) {
        this(id, conversaId, papel, texto, blocosJson, linksJson, provedor, modelo, criadaEm, null, null, null);
    }
}
