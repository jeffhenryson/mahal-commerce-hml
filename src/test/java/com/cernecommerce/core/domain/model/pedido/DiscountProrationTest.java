package com.cernecommerce.core.domain.model.pedido;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDV-F014 — o rateio do desconto de conta entre as linhas.
 *
 * <p>Função pura, e testada com o rigor que ela pede: a razão de existir separada é justamente a
 * aritmética de arredondamento, em que "quase certo" significa centavo somindo ou duplicando na
 * conta do cliente.</p>
 */
class DiscountProrationTest {

    private static BigDecimal soma(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void distribute_splitsProportionallyToEachLine() {
        List<BigDecimal> result = DiscountProration.distribute(
                List.of(new BigDecimal("70.00"), new BigDecimal("30.00")), new BigDecimal("10.00"));

        assertThat(result.get(0)).isEqualByComparingTo("7.00");
        assertThat(result.get(1)).isEqualByComparingTo("3.00");
    }

    /**
     * O caso que faz a função existir: 10 dividido por 3 não fecha em duas casas. A soma tem que
     * bater no centavo, e a sobra vai para a linha com mais folga.
     */
    @Test
    void distribute_putsTheTruncationRemainderOnTheLineWithMostHeadroom() {
        List<BigDecimal> linhas = List.of(new BigDecimal("10.00"), new BigDecimal("10.00"),
                new BigDecimal("10.00"));

        List<BigDecimal> result = DiscountProration.distribute(linhas, new BigDecimal("10.00"));

        assertThat(soma(result)).isEqualByComparingTo("10.00");
        // 3,33 em cada, com o centavo restante na primeira (todas têm a mesma folga).
        assertThat(result.get(0)).isEqualByComparingTo("3.34");
        assertThat(result.get(1)).isEqualByComparingTo("3.33");
        assertThat(result.get(2)).isEqualByComparingTo("3.33");
    }

    @Test
    void distribute_alwaysSumsExactlyToTheRequestedDiscount() {
        List<List<BigDecimal>> cenarios = List.of(
                List.of(new BigDecimal("33.33"), new BigDecimal("66.67")),
                List.of(new BigDecimal("0.01"), new BigDecimal("99.99")),
                List.of(new BigDecimal("7.77"), new BigDecimal("7.77"), new BigDecimal("7.77")),
                List.of(new BigDecimal("100.00"), new BigDecimal("0.03"), new BigDecimal("13.31")));

        for (List<BigDecimal> linhas : cenarios) {
            for (String desconto : List.of("0.01", "1.00", "3.33", "9.99")) {
                List<BigDecimal> result = DiscountProration.distribute(linhas, new BigDecimal(desconto));
                assertThat(soma(result))
                        .as("linhas=%s desconto=%s", linhas, desconto)
                        .isEqualByComparingTo(desconto);
            }
        }
    }

    /**
     * A invariante de {@code OrderItem}: desconto maior que o bruto do item é devolução, não venda.
     * O rateio não pode ser o que a viola — nem no ajuste da sobra.
     *
     * <p>Este cenário exato pegou um bug na primeira versão: a sobra ia inteira para a <b>maior</b>
     * linha, que aqui já está no teto (99,97 de 99,98) e receberia 0,02, estourando o próprio
     * valor. Por isso a sobra é distribuída por <b>folga</b>, centavo a centavo.</p>
     */
    @Test
    void distribute_neverGivesALineMoreDiscountThanItsOwnAmount() {
        List<BigDecimal> linhas = List.of(new BigDecimal("0.01"), new BigDecimal("0.01"),
                new BigDecimal("99.98"));

        List<BigDecimal> result = DiscountProration.distribute(linhas, new BigDecimal("99.99"));

        for (int i = 0; i < linhas.size(); i++) {
            assertThat(result.get(i)).isLessThanOrEqualTo(linhas.get(i));
        }
        assertThat(soma(result)).isEqualByComparingTo("99.99");
    }

    /** Cortesia é linha de valor zero: a proporção dela é zero, sem caso especial no código. */
    @Test
    void distribute_givesZeroToCourtesyLinesByConstruction() {
        List<BigDecimal> result = DiscountProration.distribute(
                List.of(new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00")),
                new BigDecimal("10.00"));

        assertThat(result.get(1)).isEqualByComparingTo("0.00");
        assertThat(soma(result)).isEqualByComparingTo("10.00");
    }

    @Test
    void distribute_withoutDiscount_returnsZeroForEveryLine() {
        List<BigDecimal> linhas = List.of(new BigDecimal("10.00"), new BigDecimal("20.00"));

        assertThat(DiscountProration.distribute(linhas, BigDecimal.ZERO))
                .allSatisfy(v -> assertThat(v).isEqualByComparingTo("0"));
        assertThat(DiscountProration.distribute(linhas, null))
                .allSatisfy(v -> assertThat(v).isEqualByComparingTo("0"));
    }

    /** Desconto de 100%: divisão exata, sobra zero, e cada linha zerada sem estourar a invariante. */
    @Test
    void distribute_fullDiscount_zeroesEveryLineExactly() {
        List<BigDecimal> linhas = List.of(new BigDecimal("70.00"), new BigDecimal("30.00"));

        List<BigDecimal> result = DiscountProration.distribute(linhas, new BigDecimal("100.00"));

        assertThat(result.get(0)).isEqualByComparingTo("70.00");
        assertThat(result.get(1)).isEqualByComparingTo("30.00");
    }

    @Test
    void distribute_refusesDiscountGreaterThanTheBill() {
        assertThatThrownBy(() -> DiscountProration.distribute(
                List.of(new BigDecimal("10.00")), new BigDecimal("10.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que o total");
    }

    @Test
    void distribute_refusesNegativeDiscount() {
        assertThatThrownBy(() -> DiscountProration.distribute(
                List.of(new BigDecimal("10.00")), new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    /**
     * Abater de uma conta de total zero — só cortesias — não é um rateio de resultado indefinido,
     * é erro de lançamento. Na prática {@code COMANDA_ONLY_COURTESY} já barra antes, mas a função
     * é pura e não pode depender disso.
     */
    @Test
    void distribute_refusesDiscountOnAZeroTotalBill() {
        assertThatThrownBy(() -> DiscountProration.distribute(
                List.of(BigDecimal.ZERO, BigDecimal.ZERO), new BigDecimal("5.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total zero");
    }

    @Test
    void distribute_refusesEmptyLines() {
        assertThatThrownBy(() -> DiscountProration.distribute(List.of(), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A sobra é distribuída centavo a centavo: com 10,005 o laço daria 0,01 a uma linha e fecharia
     * em 10,01, mais que o desconto pedido. Quem chama normaliza antes (Money#of); aqui é recusado.
     */
    @Test
    void distribute_refusesADiscountWithAFractionOfACent() {
        assertThatThrownBy(() -> DiscountProration.distribute(
                List.of(new BigDecimal("50.00"), new BigDecimal("50.00")), new BigDecimal("10.005")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 casas");
    }

    /** Zeros à direita não são fração de centavo. */
    @Test
    void distribute_acceptsTrailingZerosBeyondTheSecondPlace() {
        List<BigDecimal> result = DiscountProration.distribute(
                List.of(new BigDecimal("50.00"), new BigDecimal("50.00")), new BigDecimal("10.000"));

        assertThat(soma(result)).isEqualByComparingTo("10.00");
    }
}
