package com.cernecommerce.core.domain.model.usermetrics;

import com.cernecommerce.core.domain.model.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Perfil > métricas: desempenho e atividade de UM usuário, servidos tanto em {@code /users/me/...}
 * (o próprio) quanto em {@code /users/{id}/...} (ficha do admin), com o mesmo formato.
 *
 * <p>O usuário é identificado pelo <b>username</b>: é o que o caixa ({@code operator}), a comanda
 * ({@code openedBy}), o item ({@code addedBy}) e a auditoria guardam. O pedido não tem operador
 * próprio — ele é o operador da sessão de caixa em que a venda caiu, como em
 * {@code GET /orders/analytics/operators}.</p>
 */
public final class UserMetrics {

    private UserMetrics() {}

    public record Performance(PdvPerformance pdv, CashPerformance cash, TablesPerformance tables) {}

    public record PdvPerformance(BigDecimal revenue, long orders, BigDecimal averageTicket) {}

    /** {@code totalDifference} = Σ (contado − esperado) das fechadas; negativo é falta. */
    public record CashPerformance(long opened, long closed, BigDecimal totalSold, long divergenceCount,
            BigDecimal totalDifference) {}

    public record TablesPerformance(long tabsOpened, long tabsClosed, long itemsLaunched) {}

    /** Receita e quantidade de pedidos pagos do operador no período. */
    public record PdvTotals(BigDecimal revenue, long orders) {}

    /** Sessão fechada no período: o total vendido sai do resumo, a diferença já está gravada. */
    public record ClosedSession(Long id, BigDecimal differenceAmount) {}

    /** {@code difference} e {@code closedAt} nulos no caixa aberto; {@code totalSold} é o parcial. */
    public record CashSessionItem(Long id, Instant openedAt, Instant closedAt, String status,
            BigDecimal totalSold, BigDecimal difference) {}

    public enum AccessType { LOGIN, LOGOUT, LOGIN_FAILED }

    /** Linha crua de {@code audit_logs}, antes de virar {@link AccessLogItem}. */
    public record AuthEvent(Long id, Instant timestamp, String action, String ipAddress, String userAgent) {}

    public record AccessLogItem(Long id, Instant timestamp, AccessType type, String ipAddress, String userAgent) {}

    /** {@code date} é o dia no fuso da loja; {@code lastLogoutAt} nulo se não houve logout no dia. */
    public record AttendanceDay(LocalDate date, Instant firstLoginAt, Instant lastLogoutAt) {}

    public record Attendance(int daysWithAccess, List<AttendanceDay> days) {}

    /** {@code status} e {@code channel} nos mesmos valores de {@code OrderAdminResponseDTO}. */
    public record CustomerOrderItem(Long id, Instant createdAt, BigDecimal total, String status, String channel,
            long itemsCount) {}

    /**
     * {@code totalSpent}/{@code orders}: pedidos pagos do período (sem cancelados, reembolsados ou não
     * pagos). {@code lastOrderAt}: último pedido do cliente em qualquer data, ou nulo.
     */
    public record CustomerOrdersSummary(BigDecimal totalSpent, long orders, Instant lastOrderAt) {}

    public record CustomerOrders(CustomerOrdersSummary summary, PageResult<CustomerOrderItem> page) {}
}
