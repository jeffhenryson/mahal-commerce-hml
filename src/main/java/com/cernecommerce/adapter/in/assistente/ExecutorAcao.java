package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.dtos.response.AssistenteLinkDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.function.Function;

/**
 * Executa uma ação confirmada, lendo só os parâmetros guardados. Roda na thread do request de
 * confirmar, como o usuário logado, chamando o controller do recurso.
 */
public record ExecutorAcao(String tipo, Function<JsonNode, Execucao> executar) {

    /** Texto mostrado no cartão depois de executar, e links para o que foi criado/alterado. */
    public record Execucao(String resultado, List<AssistenteLinkDTO> links) { }
}
