package com.cernecommerce.core.ports.out.user;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.usermetrics.UserMetrics;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Agregados de {@link UserMetrics}. Todos os períodos são inclusivos nas duas pontas. */
public interface UserMetricsRepository {

    /** Pedidos pagos cuja sessão de caixa é de {@code operator}, por {@code createdAt}. */
    UserMetrics.PdvTotals pdvTotals(String operator, Instant from, Instant to);

    long cashSessionsOpened(String operator, Instant from, Instant to);

    List<UserMetrics.ClosedSession> cashSessionsClosed(String operator, Instant from, Instant to);

    long tabsOpened(String username, Instant from, Instant to);

    /** Das comandas abertas por ele no período, as fechadas também no período. */
    long tabsClosed(String username, Instant from, Instant to);

    /** Linhas lançadas por ele em qualquer comanda, por {@code addedAt}. */
    long itemsLaunched(String username, Instant from, Instant to);

    /** {@code from}/{@code to} opcionais. Ordem: timestamp desc. */
    PageResult<UserMetrics.AuthEvent> authEvents(String username, Collection<String> actions, Instant from,
            Instant to, int page, int size);

    /** Sem paginação, ordem cronológica — base da frequência por dia. */
    List<UserMetrics.AuthEvent> authEventsChronological(String username, Collection<String> actions, Instant from,
            Instant to);

    UserMetrics.CustomerOrdersSummary customerOrdersSummary(Long customerId, Instant from, Instant to);

    /** Todos os status. Ordem: createdAt desc. */
    PageResult<UserMetrics.CustomerOrderItem> customerOrders(Long customerId, Instant from, Instant to, int page,
            int size);
}
