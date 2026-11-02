package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pedido.SalesAnalytics;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.pedido.SalesGoal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * Análise de Vendas (pendência 9). Período obrigatório, até 366 dias ({@code 400 INVALID_REPORT_PERIOD}),
 * como no summary.
 */
public interface SalesAnalyticsUseCase {

    List<SalesAnalytics.HeatmapCell> heatmap(SalesChannel channel, String warehouseCode, Instant from, Instant to);

    List<SalesAnalytics.PaymentMethodTotal> paymentMethods(SalesChannel channel, String warehouseCode, Instant from,
            Instant to);

    List<SalesAnalytics.OperatorTotal> operators(SalesChannel channel, String warehouseCode, Instant from,
            Instant to);

    SalesAnalytics.CustomerRanking customers(SalesChannel channel, Instant from, Instant to, int page, int size,
            SalesAnalytics.CustomerSort sort);

    List<SalesAnalytics.SlowMover> slowMovers(String warehouseCode, Instant from, Instant to, BigDecimal maxSold,
            int limit);

    /** @throws com.cernecommerce.core.domain.exception.pedido.SalesGoalNotFoundException sem meta no mês */
    SalesGoal getGoal(YearMonth month);

    /** Grava ou substitui a meta do mês (idempotente). */
    SalesGoal saveGoal(YearMonth month, BigDecimal amount, String username);
}
