package com.cernecommerce.adapter.in.dtos.response;

import java.util.List;

/**
 * {@code turnoEmAndamento}: o servidor ainda está escrevendo uma resposta nesta conversa (o stream
 * pode ter caído no front). Quando passa a {@code false} sem resposta nova, ela não vai chegar.
 */
public record AssistenteConversaDTO(String id, String titulo, List<AssistenteMensagemDTO> mensagens,
        boolean turnoEmAndamento) { }
