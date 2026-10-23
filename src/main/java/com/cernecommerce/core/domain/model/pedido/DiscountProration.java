package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.model.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Rateio de um desconto de <b>nível de conta</b> entre as linhas que a compõem (PDV-F014).
 *
 * <p>Existe porque o desconto de fim de noite é pedido sobre o total ("tira 20 reais"), mas o
 * modelo guarda desconto <b>por item</b>: {@code Order.discountAmount} é a soma dos
 * {@code OrderItem.discountAmount}, e é sobre o líquido de cada item que o cashback é creditado e
 * a margem é calculada. Um desconto solto no pedido faria a casa pagar cashback sobre dinheiro que
 * não recebeu e mostraria margem cheia numa venda abatida.</p>
 *
 * <p><b>Função pura, sem estado.</b> A aritmética de arredondamento é o motivo de ela existir
 * separada: distribuir proporcionalmente com duas casas quase nunca fecha na soma, e a sobra tem
 * que ir para algum lugar determinístico em vez de sumir ou duplicar centavos.</p>
 */
public final class DiscountProration {

    private DiscountProration() {
    }

    /**
     * Distribui {@code totalDiscount} entre as linhas, proporcionalmente ao valor de cada uma.
     *
     * <p>Garantias, todas verificadas por teste:</p>
     * <ul>
     *   <li>a soma do resultado é <b>exatamente</b> {@code totalDiscount} — a sobra de centavos do
     *       truncamento vai, centavo a centavo, para as linhas com mais folga;</li>
     *   <li>nenhuma linha recebe desconto maior que o próprio valor, o que violaria a invariante
     *       {@code discountAmount <= quantity × unitPrice} de {@code OrderItem};</li>
     *   <li>linha de valor zero — a cortesia — recebe zero por construção, sem caso especial: a
     *       proporção dela é zero.</li>
     * </ul>
     *
     * @param lineAmounts valor bruto de cada linha, na ordem em que serão gravadas
     * @param totalDiscount desconto a ratear; zero ou nulo devolve uma lista de zeros
     * @throws IllegalArgumentException se o desconto for negativo, tiver mais de 2 casas, for maior
     *         que a soma das linhas, ou
     *         se houver desconto a ratear sobre um total zero — abater de uma conta que não existe
     *         é erro de lançamento, não um rateio de resultado indefinido
     */
    public static List<BigDecimal> distribute(List<BigDecimal> lineAmounts, BigDecimal totalDiscount) {
        if (lineAmounts == null || lineAmounts.isEmpty()) {
            throw new IllegalArgumentException("lineAmounts não pode ser vazio");
        }
        BigDecimal discount = totalDiscount == null ? BigDecimal.ZERO : totalDiscount;
        if (discount.signum() < 0) {
            throw new IllegalArgumentException("desconto não pode ser negativo: " + discount);
        }
        // A sobra é distribuída centavo a centavo: com uma fração de centavo (0,005) o laço passaria
        // do desconto pedido. Quem chama normaliza antes (Money#of); aqui é a rede de segurança.
        if (discount.stripTrailingZeros().scale() > Money.MONEY_SCALE) {
            throw new IllegalArgumentException("desconto com mais de 2 casas decimais: " + discount);
        }

        List<BigDecimal> result = new ArrayList<>(lineAmounts.size());
        if (discount.signum() == 0) {
            for (int i = 0; i < lineAmounts.size(); i++) {
                result.add(BigDecimal.ZERO);
            }
            return result;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : lineAmounts) {
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("valor de linha inválido no rateio: " + amount);
            }
            total = total.add(amount);
        }
        if (total.signum() == 0) {
            throw new IllegalArgumentException("não há como ratear desconto numa conta de total zero");
        }
        if (discount.compareTo(total) > 0) {
            throw new IllegalArgumentException(
                    "desconto " + discount + " maior que o total da conta " + total);
        }

        // Proporcional, com truncamento para BAIXO: assim a soma das partes nunca ultrapassa o
        // desconto pedido, e a sobra é sempre não-negativa. Arredondar cada linha para o mais
        // próximo poderia estourar o total e obrigar a tirar centavos de volta.
        BigDecimal distribuido = BigDecimal.ZERO;
        for (BigDecimal amount : lineAmounts) {
            BigDecimal parte = amount.multiply(discount).divide(total, 2, RoundingMode.DOWN);
            result.add(parte);
            distribuido = distribuido.add(parte);
        }

        // A sobra do truncamento é sempre menor que um centavo por linha, e vai centavo a centavo
        // para as linhas com mais folga primeiro.
        //
        // NÃO basta jogá-la toda na maior linha: quando o desconto é quase o total da conta, a
        // maior linha já está no teto e o centavo a empurraria acima do próprio valor, violando a
        // invariante de OrderItem (desconto que passa do bruto é devolução, não venda). Uma conta
        // de 0,01 + 0,01 + 99,98 com 99,99 de desconto cai exatamente nisso.
        //
        // A capacidade total sempre basta: a soma dos tetos é o total da conta, que já foi
        // verificado como maior ou igual ao desconto.
        BigDecimal sobra = discount.subtract(distribuido);
        if (sobra.signum() > 0) {
            List<Integer> porFolga = new ArrayList<>();
            for (int i = 0; i < lineAmounts.size(); i++) {
                porFolga.add(i);
            }
            porFolga.sort((a, b) -> lineAmounts.get(b).subtract(result.get(b))
                    .compareTo(lineAmounts.get(a).subtract(result.get(a))));

            BigDecimal centavo = new BigDecimal("0.01");
            while (sobra.signum() > 0) {
                boolean coube = false;
                for (int i : porFolga) {
                    if (sobra.signum() == 0) {
                        break;
                    }
                    if (result.get(i).add(centavo).compareTo(lineAmounts.get(i)) <= 0) {
                        result.set(i, result.get(i).add(centavo));
                        sobra = sobra.subtract(centavo);
                        coube = true;
                    }
                }
                if (!coube) {
                    throw new IllegalStateException(
                            "não há folga para distribuir a sobra do rateio: " + sobra);
                }
            }
        }

        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).compareTo(lineAmounts.get(i)) > 0) {
                throw new IllegalStateException("rateio produziu desconto maior que a linha " + i
                        + ": " + result.get(i) + " > " + lineAmounts.get(i));
            }
        }
        return result;
    }
}
