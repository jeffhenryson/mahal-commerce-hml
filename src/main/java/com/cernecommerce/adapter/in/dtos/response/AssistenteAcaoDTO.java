package com.cernecommerce.adapter.in.dtos.response;

import java.util.List;

/** Ação proposta pelo modelo como o front a mostra. Os campos são os que o servidor guardou. */
public record AssistenteAcaoDTO(String acaoId, String titulo, List<AssistenteCampoDTO> campos, String estado,
        String expiraEm, String resultado, List<AssistenteLinkDTO> links) { }
