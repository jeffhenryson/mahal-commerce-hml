package com.cernecommerce.core.domain.model.assistente;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Preço por milhão de tokens de um provedor, para o custo estimado do relatório de uso. */
public record PrecoTokens(BigDecimal entrada, BigDecimal saida, BigDecimal cache) {

    private static final BigDecimal MILHAO = BigDecimal.valueOf(1_000_000);

    public BigDecimal custo(UsoTokens uso) {
        return BigDecimal.valueOf(uso.entrada()).multiply(entrada)
                .add(BigDecimal.valueOf(uso.saida()).multiply(saida))
                .add(BigDecimal.valueOf(uso.cache()).multiply(cache))
                .divide(MILHAO, 4, RoundingMode.HALF_UP);
    }
}
