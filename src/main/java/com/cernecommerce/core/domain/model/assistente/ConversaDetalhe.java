package com.cernecommerce.core.domain.model.assistente;

import java.util.List;

/** Conversa com as mensagens em ordem e as ações propostas em cada uma. */
public record ConversaDetalhe(Conversa conversa, List<Mensagem> mensagens, List<AcaoPendente> acoes) {
}
