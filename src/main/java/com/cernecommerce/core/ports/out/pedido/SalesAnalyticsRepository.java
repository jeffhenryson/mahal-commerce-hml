package com.cernecommerce.core.ports.out.pedido;

import com.cernecommerce.core.domain.model.pedido.SalesAnalytics;
import com.cernecommerce.core.domain.model.pedido.SalesGoal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Leituras da Análise de Vendas (pendência 9). Filtros opcionais nulos não filtram. "Pago" é o mesmo
 * conjunto da receita do summary: PAGO, SEPARADO, ENVIADO, ENTREGUE, CONCLUIDO e RESERVADO.
 */
public interface SalesAnalyticsRepository {

    /** Os pedidos pagos do período, crus: o agrupamento por dia/hora é feito no fuso da loja, em Java. */
    List<SalesAnalytics.PaidOrder> paidOrders(String channel, String warehouseCode, Instant from, Instant to);

    /**
     * Linhas de pagamento CAPTURED e ON_ACCOUNT (Marcado) dos pedidos pagos, por método, com o troco do
     * dinheiro já descontado. Sem REFUNDED, CORRECTED nem CANCELLED.
     */
    List<SalesAnalytics.PaymentMethodTotal> paymentTotals(String channel, String warehouseCode, Instant from,
            Instant to);

    /** Operador (username) de cada sessão de caixa. */
    Map<Long, String> sessionOperators(Collection<Long> sessionIds);

    /** Id do usuário operador por username. */
    Map<String, Long> operatorUserIds(Collection<String> usernames);

    /** Encalhados (ver {@link SalesAnalytics.SlowMover}), já filtrados, ordenados e limitados. */
    List<SalesAnalytics.SlowMover> slowMovers(String warehouseCode, Instant from, Instant to, BigDecimal maxSold,
            int limit);

    /** @return {@code false} se não havia meta para o mês */
    boolean deleteGoal(YearMonth month);

    Optional<SalesGoal> findGoal(YearMonth month);

    SalesGoal saveGoal(SalesGoal goal);
}
