package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.model.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Regra do <b>desconto do chefe</b>: um percentual inteiro de 1 a 5% sobre a <b>base elegível</b> — o
 * líquido das linhas de categoria dentro dos benefícios, depois de kit e promoção.
 *
 * <p>O valor continua rateado por linha ({@code OrderItem.discountAmount}), porque é sobre o líquido de
 * cada linha que a margem é calculada. O percentual é o que o operador escolhe; o valor em R$ é
 * consequência, e o servidor confere que um bate com o outro.</p>
 *
 * <p><b>Função pura, sem estado</b>, irmã de {@link DiscountProration}.</p>
 */
public final class BossDiscount {

    /** Percentual mínimo e máximo do desconto do chefe. */
    public static final int MIN_PERCENT = 1;
    public static final int MAX_PERCENT = 5;

    /** Tolerância de arredondamento: um centavo por linha elegível que recebeu parte do rateio. */
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private BossDiscount() {
    }

    /** Valor esperado do desconto: {@code base × percent / 100}, em 2 casas, arredondado para baixo. */
    public static BigDecimal expected(int percent, BigDecimal eligibleBase) {
        BigDecimal base = eligibleBase == null ? BigDecimal.ZERO : eligibleBase.max(BigDecimal.ZERO);
        return base.multiply(BigDecimal.valueOf(percent))
                .divide(Money.HUNDRED, Money.MONEY_SCALE, RoundingMode.DOWN);
    }

    /**
     * Confere o desconto concedido contra o percentual.
     *
     * @param percent percentual pedido (já validado entre 1 e o teto)
     * @param granted desconto concedido pelo operador (sem kit nem promoção)
     * @param eligibleBase líquido das linhas elegíveis, antes do desconto do chefe
     * @param eligibleLines quantas linhas elegíveis dividem o rateio (a tolerância é 1 centavo por linha)
     * @throws DiscountLimitExceededException se o valor não bate com o percentual
     */
    public static void requireMatches(int percent, BigDecimal granted, BigDecimal eligibleBase, int eligibleLines) {
        BigDecimal expected = expected(percent, eligibleBase);
        BigDecimal tolerance = CENT.multiply(BigDecimal.valueOf(Math.max(1, eligibleLines)));
        BigDecimal actual = granted == null ? BigDecimal.ZERO : granted;
        if (actual.subtract(expected).abs().compareTo(tolerance) > 0) {
            throw new DiscountLimitExceededException("Desconto do chefe de " + percent + "% sobre R$ "
                    + eligibleBase + " (itens elegíveis) deveria ser R$ " + expected + ", veio R$ " + actual + ".");
        }
    }

    /** Linhas que entram na base: as que não são de categoria fora dos benefícios. */
    public static List<OrderItem> eligibleLines(List<OrderItem> items) {
        return items.stream().filter(i -> !i.benefitsExcluded()).toList();
    }
}
