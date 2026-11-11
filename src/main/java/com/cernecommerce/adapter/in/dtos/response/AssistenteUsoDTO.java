package com.cernecommerce.adapter.in.dtos.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Uso do assistente no mês ({@code GET /assistente/uso}). {@code tokensNoLimite} = entrada + saída (o
 * cache não conta). {@code custoEstimado} nulo quando falta o preço de algum provedor usado.
 */
public record AssistenteUsoDTO(String mes, Long limiteMensal, long tokensNoLimite, long tokensEntrada,
        long tokensSaida, long tokensCache, BigDecimal custoEstimado, String moeda, List<Usuario> usuarios) {

    public record Usuario(String username, long respostas, long tokensEntrada, long tokensSaida, long tokensCache,
            BigDecimal custoEstimado) { }
}
