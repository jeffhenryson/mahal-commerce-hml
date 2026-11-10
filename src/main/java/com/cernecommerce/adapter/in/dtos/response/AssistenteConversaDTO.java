package com.cernecommerce.adapter.in.dtos.response;

import java.util.List;

public record AssistenteConversaDTO(String id, String titulo, List<AssistenteMensagemDTO> mensagens) { }
