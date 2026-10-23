package com.cernecommerce.core.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Constantes de escala e arredondamento compartilhadas pelos cálculos financeiros do domínio. */
public final class Money {

    /** Casas decimais de valores monetários — alinhado com {@code NUMERIC(14,2)} no schema. */
    public static final int MONEY_SCALE = 2;

    /** Casas decimais dos percentuais derivados (markup, margem, desconto, cashback). */
    public static final int PERCENT_SCALE = 2;

    /** Escala intermediária usada em divisões antes do arredondamento final. */
    public static final int INTERMEDIATE_SCALE = 6;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Normaliza um valor monetário que veio de fora (cliente HTTP, integração) para a escala do
     * schema: {@code setScale(MONEY_SCALE, HALF_UP)}. Existe porque o cliente soma preço × quantidade
     * em ponto flutuante e manda {@code 76.28999999999999} querendo dizer 76,29 — comparado cru, o
     * valor era recusado como insuficiente, e o que passava era gravado já arredondado pelo
     * {@code NUMERIC(14,2)}, diferente do que a regra tinha validado.
     *
     * <p>Só para <b>dinheiro</b>. Quantidade (3 casas) e percentual (4 casas) nunca passam por aqui.</p>
     *
     * @return {@code null} quando {@code value} é nulo
     */
    public static BigDecimal of(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, ROUNDING);
    }

    private Money() {
    }
}
