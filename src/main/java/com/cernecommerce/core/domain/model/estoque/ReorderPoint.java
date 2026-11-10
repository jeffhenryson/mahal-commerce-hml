package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InvalidReorderCriticalQuantityException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ponto de reposição (quantidade mínima) de um SKU em um depósito. Quando o
 * {@link StockBalance} correspondente atinge {@code minQuantity} (saldo igual ou menor), o SKU
 * entra no alerta e uma notificação de reposição é disparada.
 *
 * <p>{@code criticalQuantity} é o corte do nível {@link AlertSeverity#CRITICO}. Nulo significa
 * "padrão": metade do mínimo — {@code floor(min/2)} quando o mínimo é inteiro, {@code min/2}
 * quando é fracionário (KG, L). Quando informado, precisa ficar em {@code 0 ≤ c < min}.</p>
 *
 * <p>{@code minQuantity = 0} desliga o alerta: nenhum saldo, nem zero ou negativo, gera alerta.</p>
 */
public record ReorderPoint(Long id, String sku, Long warehouseId, BigDecimal minQuantity,
                           BigDecimal criticalQuantity) {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    public ReorderPoint {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (minQuantity == null || minQuantity.signum() < 0) {
            throw new IllegalArgumentException("minQuantity deve ser maior ou igual a zero");
        }
        if (criticalQuantity != null && !isValidCritical(criticalQuantity, minQuantity)) {
            throw new InvalidReorderCriticalQuantityException(criticalQuantity, minQuantity);
        }
    }

    /** Ponto sem crítico próprio (padrão de 50%). */
    public ReorderPoint(Long id, String sku, Long warehouseId, BigDecimal minQuantity) {
        this(id, sku, warehouseId, minQuantity, null);
    }

    /** Cria um novo ponto de reposição (sem id), com crítico padrão. */
    public static ReorderPoint create(String sku, Long warehouseId, BigDecimal minQuantity) {
        return new ReorderPoint(null, sku, warehouseId, minQuantity, null);
    }

    /** Cria um novo ponto de reposição (sem id). {@code criticalQuantity} nulo = padrão. */
    public static ReorderPoint create(String sku, Long warehouseId, BigDecimal minQuantity,
            BigDecimal criticalQuantity) {
        return new ReorderPoint(null, sku, warehouseId, minQuantity, criticalQuantity);
    }

    /** Reconstitui um ponto de reposição a partir de persistência, com crítico padrão. */
    public static ReorderPoint of(Long id, String sku, Long warehouseId, BigDecimal minQuantity) {
        return new ReorderPoint(id, sku, warehouseId, minQuantity, null);
    }

    /** Reconstitui um ponto de reposição a partir de persistência. */
    public static ReorderPoint of(Long id, String sku, Long warehouseId, BigDecimal minQuantity,
            BigDecimal criticalQuantity) {
        return new ReorderPoint(id, sku, warehouseId, minQuantity, criticalQuantity);
    }

    /** {@code true} se {@code critical} é um crítico aceitável para {@code min}: {@code 0 ≤ c < min}. */
    public static boolean isValidCritical(BigDecimal critical, BigDecimal min) {
        return critical.signum() >= 0 && critical.compareTo(min) < 0;
    }

    /**
     * Crítico efetivo: o configurado ou, se nulo, metade do mínimo — {@code floor(min/2)} para
     * mínimo inteiro (com saldo inteiro dá o mesmo corte do antigo {@code q <= min × 0,5}) e
     * {@code min/2} para mínimo fracionário.
     */
    public BigDecimal effectiveCriticalQuantity() {
        if (criticalQuantity != null) {
            return criticalQuantity;
        }
        if (minQuantity.stripTrailingZeros().scale() <= 0) {
            return minQuantity.divide(TWO, 0, RoundingMode.FLOOR);
        }
        return minQuantity.divide(TWO);
    }

    /** {@code true} se {@code quantity} está estritamente abaixo do ponto de reposição. */
    public boolean isBelow(BigDecimal quantity) {
        return quantity.compareTo(minQuantity) < 0;
    }

    /**
     * {@code true} se {@code quantity} atingiu o ponto (igual ou abaixo) e o alerta está ligado
     * ({@code minQuantity > 0}). É o gatilho da notificação: avisa no mesmo momento em que o
     * produto aparece no alerta da tela.
     */
    public boolean isAtOrBelow(BigDecimal quantity) {
        return minQuantity.signum() > 0 && quantity.compareTo(minQuantity) <= 0;
    }

    /**
     * Classifica a severidade do alerta para {@code quantity} — usada pelo painel de KPIs
     * ({@code GET /estoque/summary}, replicada em SQL em {@code ReorderPointJpaRepository.countAlertsRaw}).
     *
     * <pre>
     * min ≤ 0          → OK (alerta desligado)
     * q ≤ crítico      → CRITICO (saldo ≤ 0 sempre cai aqui, porque crítico ≥ 0)
     * q &lt; min          → ABAIXO
     * q == min         → NO_LIMITE
     * q &gt; min          → OK
     * </pre>
     */
    public AlertSeverity severityFor(BigDecimal quantity) {
        if (minQuantity.signum() <= 0) {
            return AlertSeverity.OK;
        }
        if (quantity.compareTo(effectiveCriticalQuantity()) <= 0) {
            return AlertSeverity.CRITICO;
        }
        int cmp = quantity.compareTo(minQuantity);
        if (cmp < 0) {
            return AlertSeverity.ABAIXO;
        }
        return cmp == 0 ? AlertSeverity.NO_LIMITE : AlertSeverity.OK;
    }
}
