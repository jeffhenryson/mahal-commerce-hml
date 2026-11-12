package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InvalidReorderCriticalQuantityException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReorderPointTest {

    @Test
    void create_buildsReorderPointWithoutId() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.id()).isNull();
        assertThat(reorderPoint.sku()).isEqualTo("ESS-001");
        assertThat(reorderPoint.warehouseId()).isEqualTo(1L);
        assertThat(reorderPoint.minQuantity()).isEqualByComparingTo("10.000");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        ReorderPoint reorderPoint = ReorderPoint.of(9L, "ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.id()).isEqualTo(9L);
        assertThat(reorderPoint.sku()).isEqualTo("ESS-001");
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> ReorderPoint.create("  ", 1L, new BigDecimal("10.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenSkuIsNull() {
        assertThatThrownBy(() -> ReorderPoint.create(null, 1L, new BigDecimal("10.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseIdIsNull() {
        assertThatThrownBy(() -> ReorderPoint.create("ESS-001", null, new BigDecimal("10.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenMinQuantityIsNull() {
        assertThatThrownBy(() -> ReorderPoint.create("ESS-001", 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenMinQuantityIsNegative() {
        assertThatThrownBy(() -> ReorderPoint.create("ESS-001", 1L, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_aceitaMinQuantityZero() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, BigDecimal.ZERO);

        assertThat(reorderPoint.minQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void isBelow_trueQuandoQuantidadeEstaAbaixoDoPonto() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.isBelow(new BigDecimal("9.999"))).isTrue();
    }

    @Test
    void isBelow_falseQuandoQuantidadeEstaIgualAoPonto() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.isBelow(new BigDecimal("10.000"))).isFalse();
    }

    @Test
    void isBelow_falseQuandoQuantidadeEstaAcimaDoPonto() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.isBelow(new BigDecimal("10.001"))).isFalse();
    }

    @Test
    void isAtOrBelow_trueNoLimiteEAbaixo_falseAcimaOuComMinimoZero() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("7"));

        assertThat(reorderPoint.isAtOrBelow(new BigDecimal("7"))).isTrue();
        assertThat(reorderPoint.isAtOrBelow(new BigDecimal("6"))).isTrue();
        assertThat(reorderPoint.isAtOrBelow(new BigDecimal("8"))).isFalse();
        assertThat(ReorderPoint.create("ESS-001", 1L, BigDecimal.ZERO).isAtOrBelow(BigDecimal.ZERO)).isFalse();
    }

    // ── criticalQuantity ─────────────────────────────────────────────────────

    @Test
    void criticalQuantity_aceitaZeroEMenorQueMinimo() {
        assertThat(ReorderPoint.create("ESS-001", 1L, new BigDecimal("7"), BigDecimal.ZERO).criticalQuantity())
                .isEqualByComparingTo("0");
        assertThat(ReorderPoint.create("ESS-001", 1L, new BigDecimal("7"), new BigDecimal("6")).criticalQuantity())
                .isEqualByComparingTo("6");
    }

    @Test
    void criticalQuantity_recusaIgualAoMinimoOuNegativo() {
        assertThatThrownBy(() -> ReorderPoint.create("ESS-001", 1L, new BigDecimal("7"), new BigDecimal("7")))
                .isInstanceOf(InvalidReorderCriticalQuantityException.class);
        assertThatThrownBy(() -> ReorderPoint.create("ESS-001", 1L, new BigDecimal("7"), new BigDecimal("-1")))
                .isInstanceOf(InvalidReorderCriticalQuantityException.class);
    }

    @Test
    void effectiveCriticalQuantity_padraoFloorParaInteiroEMetadeParaFracionario() {
        assertThat(ReorderPoint.create("ESS-001", 1L, new BigDecimal("7")).effectiveCriticalQuantity())
                .isEqualByComparingTo("3");
        assertThat(ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000")).effectiveCriticalQuantity())
                .isEqualByComparingTo("5");
        assertThat(ReorderPoint.create("ESS-001", 1L, new BigDecimal("2.5")).effectiveCriticalQuantity())
                .isEqualByComparingTo("1.25");
    }

    // ── severityFor ──────────────────────────────────────────────────────────

    @Test
    void severityFor_exemploDoLimiteSete() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("7"));

        assertThat(reorderPoint.severityFor(new BigDecimal("8"))).isEqualTo(AlertSeverity.OK);
        assertThat(reorderPoint.severityFor(new BigDecimal("7"))).isEqualTo(AlertSeverity.NO_LIMITE);
        assertThat(reorderPoint.severityFor(new BigDecimal("6"))).isEqualTo(AlertSeverity.ABAIXO);
        assertThat(reorderPoint.severityFor(new BigDecimal("4"))).isEqualTo(AlertSeverity.ABAIXO);
        assertThat(reorderPoint.severityFor(new BigDecimal("3"))).isEqualTo(AlertSeverity.CRITICO);
        assertThat(reorderPoint.severityFor(BigDecimal.ZERO)).isEqualTo(AlertSeverity.CRITICO);
        assertThat(reorderPoint.severityFor(new BigDecimal("-1"))).isEqualTo(AlertSeverity.CRITICO);
    }

    @Test
    void severityFor_usaCriticoConfigurado() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("7"), new BigDecimal("1"));

        assertThat(reorderPoint.severityFor(new BigDecimal("2"))).isEqualTo(AlertSeverity.ABAIXO);
        assertThat(reorderPoint.severityFor(new BigDecimal("1"))).isEqualTo(AlertSeverity.CRITICO);
    }

    @Test
    void severityFor_criticoZeroSoPegaSaldoZeradoOuNegativo() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("7"), BigDecimal.ZERO);

        assertThat(reorderPoint.severityFor(new BigDecimal("1"))).isEqualTo(AlertSeverity.ABAIXO);
        assertThat(reorderPoint.severityFor(BigDecimal.ZERO)).isEqualTo(AlertSeverity.CRITICO);
    }

    @Test
    void severityFor_fracionario() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("2.5"));

        assertThat(reorderPoint.severityFor(new BigDecimal("1.25"))).isEqualTo(AlertSeverity.CRITICO);
        assertThat(reorderPoint.severityFor(new BigDecimal("1.3"))).isEqualTo(AlertSeverity.ABAIXO);
        assertThat(reorderPoint.severityFor(new BigDecimal("2.500"))).isEqualTo(AlertSeverity.NO_LIMITE);
    }

    @Test
    void severityFor_minimoZeroNuncaAlerta() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, BigDecimal.ZERO);

        assertThat(reorderPoint.severityFor(BigDecimal.ZERO)).isEqualTo(AlertSeverity.OK);
        assertThat(reorderPoint.severityFor(new BigDecimal("-2"))).isEqualTo(AlertSeverity.OK);
    }
}
