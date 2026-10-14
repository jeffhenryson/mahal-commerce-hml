package com.cernecommerce.core.domain.model.pagamento;

import com.cernecommerce.core.domain.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma forma de pagamento do pedido no estorno parcial (PED-F004): o peso dela na venda e o que
 * ainda dá para devolver por ela.
 */
public record RefundPot(OrderPayment payment, BigDecimal weight, BigDecimal remaining) {

    /**
     * Rateia {@code total} pelos pesos, em centavos, sem passar do restante de nenhuma forma. O que
     * sobra do arredondamento (ou de uma forma que já não tem restante) vai para as de maior peso.
     * {@code total} não pode passar da soma dos restantes — quem chama limita.
     *
     * @return a parte de cada forma, na mesma ordem de {@code pots}
     */
    public static List<BigDecimal> allocate(BigDecimal total, List<RefundPot> pots) {
        BigDecimal weightSum = pots.stream()
                .filter(p -> p.remaining().signum() > 0)
                .map(RefundPot::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (RefundPot pot : pots) {
            BigDecimal share = pot.remaining().signum() <= 0 || weightSum.signum() <= 0 ? BigDecimal.ZERO
                    : total.multiply(pot.weight()).divide(weightSum, Money.MONEY_SCALE, RoundingMode.DOWN)
                            .min(pot.remaining());
            shares.add(share);
            assigned = assigned.add(share);
        }
        BigDecimal leftover = total.subtract(assigned);
        List<Integer> byWeight = new ArrayList<>();
        for (int i = 0; i < pots.size(); i++) {
            byWeight.add(i);
        }
        byWeight.sort((a, b) -> pots.get(b).weight().compareTo(pots.get(a).weight()));
        for (int i : byWeight) {
            if (leftover.signum() <= 0) {
                break;
            }
            BigDecimal room = pots.get(i).remaining().subtract(shares.get(i));
            BigDecimal add = leftover.min(room);
            if (add.signum() > 0) {
                shares.set(i, shares.get(i).add(add));
                leftover = leftover.subtract(add);
            }
        }
        return shares;
    }
}
