package com.cernecommerce.adapter.in.dtos.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code provedor}/{@code modelo}: quem respondeu (só nas mensagens do assistente).
 * {@code feedback}: avaliação do usuário; {@code null} quando não avaliada ou mensagem do usuário.
 */
public record AssistenteMensagemDTO(String id, String papel, String texto, List<AssistenteBlocoDTO> blocos,
        List<AssistenteLinkDTO> links, List<AssistenteAcaoDTO> acoes, String provedor, String modelo,
        Instant criadaEm, AssistenteFeedbackDTO feedback) { }
