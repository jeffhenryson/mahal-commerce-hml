package com.cernecommerce.core.domain.model.assistente;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Uso do assistente num mês. {@code custoEstimado} é {@code null} quando falta o preço de algum
 * provedor usado (propriedades {@code assistente.preco.*}).
 */
public record UsoMensal(YearMonth mes, Long limiteMensal, long tokensNoLimite, UsoTokens total,
        BigDecimal custoEstimado, String moeda, List<PorUsuario> usuarios) {

    public record PorUsuario(String username, long respostas, UsoTokens uso, BigDecimal custoEstimado) { }
}
