package com.cernecommerce.adapter.in.dtos.response;

import java.util.List;

/** Resposta de confirmar/recusar. */
public record AssistenteAcaoResultadoDTO(String estado, String resultado, List<AssistenteLinkDTO> links) { }
