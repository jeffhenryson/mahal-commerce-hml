package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.usermetrics.UserMetrics;
import com.cernecommerce.core.ports.out.user.UserMetricsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Perfil > métricas. JPQL montado só com os filtros presentes, mesmo motivo de
 * {@link SalesAnalyticsRepositoryImpl}: {@code :p IS NULL OR ...} é frágil no Postgres com parâmetro
 * nulo sem tipo.
 */
@Repository
@Transactional(readOnly = true)
public class UserMetricsRepositoryImpl implements UserMetricsRepository {

    private static final List<String> PAID = CustomerInsightsRepositoryImpl.PAID_STATUSES;

    @PersistenceContext
    private EntityManager em;

    @Override
    public UserMetrics.PdvTotals pdvTotals(String operator, Instant from, Instant to) {
        Object[] r = em.createQuery("""
                SELECT COALESCE(SUM(o.netAmount), 0), COUNT(o)
                FROM OrderEntity o, CashRegisterSessionEntity s
                WHERE s.id = o.sessionId AND s.operator = :operator AND o.status IN :paid
                  AND o.createdAt >= :from AND o.createdAt <= :to
                """, Object[].class)
                .setParameter("operator", operator).setParameter("paid", PAID)
                .setParameter("from", from).setParameter("to", to)
                .getSingleResult();
        return new UserMetrics.PdvTotals((BigDecimal) r[0], ((Number) r[1]).longValue());
    }

    @Override
    public long cashSessionsOpened(String operator, Instant from, Instant to) {
        return em.createQuery("""
                SELECT COUNT(s) FROM CashRegisterSessionEntity s
                WHERE s.operator = :operator AND s.openedAt >= :from AND s.openedAt <= :to
                """, Long.class)
                .setParameter("operator", operator).setParameter("from", from).setParameter("to", to)
                .getSingleResult();
    }

    @Override
    public List<UserMetrics.ClosedSession> cashSessionsClosed(String operator, Instant from, Instant to) {
        return em.createQuery("""
                SELECT s.id, s.differenceAmount FROM CashRegisterSessionEntity s
                WHERE s.operator = :operator AND s.status = 'CLOSED'
                  AND s.closedAt >= :from AND s.closedAt <= :to
                ORDER BY s.closedAt
                """, Object[].class)
                .setParameter("operator", operator).setParameter("from", from).setParameter("to", to)
                .getResultList().stream()
                .map(r -> new UserMetrics.ClosedSession((Long) r[0], (BigDecimal) r[1]))
                .toList();
    }

    @Override
    public long tabsOpened(String username, Instant from, Instant to) {
        return em.createQuery("""
                SELECT COUNT(c) FROM ComandaEntity c
                WHERE c.openedBy = :username AND c.openedAt >= :from AND c.openedAt <= :to
                """, Long.class)
                .setParameter("username", username).setParameter("from", from).setParameter("to", to)
                .getSingleResult();
    }

    @Override
    public long tabsClosed(String username, Instant from, Instant to) {
        return em.createQuery("""
                SELECT COUNT(c) FROM ComandaEntity c
                WHERE c.openedBy = :username AND c.openedAt >= :from AND c.openedAt <= :to
                  AND c.status = 'FECHADA' AND c.closedAt <= :to
                """, Long.class)
                .setParameter("username", username).setParameter("from", from).setParameter("to", to)
                .getSingleResult();
    }

    @Override
    public long itemsLaunched(String username, Instant from, Instant to) {
        return em.createQuery("""
                SELECT COUNT(i) FROM ComandaItemEntity i
                WHERE i.addedBy = :username AND i.addedAt >= :from AND i.addedAt <= :to
                """, Long.class)
                .setParameter("username", username).setParameter("from", from).setParameter("to", to)
                .getSingleResult();
    }

    @Override
    public PageResult<UserMetrics.AuthEvent> authEvents(String username, Collection<String> actions, Instant from,
            Instant to, int page, int size) {
        String where = authFilter(from, to);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(a) FROM AuditLogEntity a WHERE " + where, Long.class);
        bindAuth(count, username, actions, from, to);
        long total = count.getSingleResult();

        TypedQuery<Object[]> q = em.createQuery("SELECT a.id, a.timestamp, a.action, a.ipAddress, a.userAgent "
                + "FROM AuditLogEntity a WHERE " + where + " ORDER BY a.timestamp DESC, a.id DESC", Object[].class);
        bindAuth(q, username, actions, from, to);
        List<UserMetrics.AuthEvent> content = q.setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(UserMetricsRepositoryImpl::authEvent).toList();
        return new PageResult<>(content, page, size, total, totalPages(total, size));
    }

    @Override
    public List<UserMetrics.AuthEvent> authEventsChronological(String username, Collection<String> actions,
            Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("SELECT a.id, a.timestamp, a.action, a.ipAddress, a.userAgent "
                + "FROM AuditLogEntity a WHERE " + authFilter(from, to) + " ORDER BY a.timestamp, a.id",
                Object[].class);
        bindAuth(q, username, actions, from, to);
        return q.getResultList().stream().map(UserMetricsRepositoryImpl::authEvent).toList();
    }

    @Override
    public UserMetrics.CustomerOrdersSummary customerOrdersSummary(Long customerId, Instant from, Instant to) {
        Object[] r = em.createQuery("""
                SELECT COALESCE(SUM(o.netAmount), 0), COUNT(o) FROM OrderEntity o
                WHERE o.customerId = :customerId AND o.status IN :paid
                  AND o.createdAt >= :from AND o.createdAt <= :to
                """, Object[].class)
                .setParameter("customerId", customerId).setParameter("paid", PAID)
                .setParameter("from", from).setParameter("to", to)
                .getSingleResult();
        Instant last = em.createQuery("SELECT MAX(o.createdAt) FROM OrderEntity o WHERE o.customerId = :customerId",
                        Instant.class)
                .setParameter("customerId", customerId).getSingleResult();
        return new UserMetrics.CustomerOrdersSummary((BigDecimal) r[0], ((Number) r[1]).longValue(), last);
    }

    @Override
    public PageResult<UserMetrics.CustomerOrderItem> customerOrders(Long customerId, Instant from, Instant to,
            int page, int size) {
        long total = em.createQuery("""
                SELECT COUNT(o) FROM OrderEntity o
                WHERE o.customerId = :customerId AND o.createdAt >= :from AND o.createdAt <= :to
                """, Long.class)
                .setParameter("customerId", customerId).setParameter("from", from).setParameter("to", to)
                .getSingleResult();
        List<UserMetrics.CustomerOrderItem> content = em.createQuery("""
                SELECT o.id, o.createdAt, o.netAmount, o.status, o.channel,
                       (SELECT COUNT(i) FROM OrderItemEntity i WHERE i.order.id = o.id)
                FROM OrderEntity o
                WHERE o.customerId = :customerId AND o.createdAt >= :from AND o.createdAt <= :to
                ORDER BY o.createdAt DESC, o.id DESC
                """, Object[].class)
                .setParameter("customerId", customerId).setParameter("from", from).setParameter("to", to)
                .setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream()
                .map(r -> new UserMetrics.CustomerOrderItem((Long) r[0], (Instant) r[1], (BigDecimal) r[2],
                        (String) r[3], (String) r[4], ((Number) r[5]).longValue()))
                .toList();
        return new PageResult<>(content, page, size, total, totalPages(total, size));
    }

    private static String authFilter(Instant from, Instant to) {
        StringBuilder sb = new StringBuilder("a.username = :username AND a.action IN :actions");
        if (from != null) sb.append(" AND a.timestamp >= :from");
        if (to != null) sb.append(" AND a.timestamp <= :to");
        return sb.toString();
    }

    private static void bindAuth(TypedQuery<?> q, String username, Collection<String> actions, Instant from,
            Instant to) {
        q.setParameter("username", username).setParameter("actions", actions);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
    }

    private static UserMetrics.AuthEvent authEvent(Object[] r) {
        return new UserMetrics.AuthEvent((Long) r[0], (Instant) r[1], (String) r[2], (String) r[3], (String) r[4]);
    }

    private static int totalPages(long total, int size) {
        return size == 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
