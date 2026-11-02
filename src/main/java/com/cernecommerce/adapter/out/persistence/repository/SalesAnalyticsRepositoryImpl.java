package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalesGoalEntity;
import com.cernecommerce.core.domain.model.auth.UserType;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics;
import com.cernecommerce.core.domain.model.pedido.SalesGoal;
import com.cernecommerce.core.ports.out.pedido.SalesAnalyticsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pendência 9. JPQL montado com só os filtros presentes: o padrão {@code :p IS NULL OR ...} é frágil
 * no Postgres com parâmetro nulo sem tipo (CRM-C008, EST-C018).
 */
@Repository
@Transactional(readOnly = true)
public class SalesAnalyticsRepositoryImpl implements SalesAnalyticsRepository {

    static final List<String> PAID = CustomerInsightsRepositoryImpl.PAID_STATUSES;
    private static final int CHUNK = 1000;

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<SalesAnalytics.PaidOrder> paidOrders(String channel, String warehouseCode, Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("SELECT o.id, o.createdAt, o.netAmount, o.customerId, o.sessionId "
                + "FROM OrderEntity o WHERE " + orderFilter("o", channel, warehouseCode), Object[].class);
        bind(q, channel, warehouseCode, from, to);
        return q.getResultList().stream()
                .map(r -> new SalesAnalytics.PaidOrder((Long) r[0], (Instant) r[1], (BigDecimal) r[2], (Long) r[3],
                        (Long) r[4]))
                .toList();
    }

    @Override
    public List<SalesAnalytics.PaymentMethodTotal> paymentTotals(String channel, String warehouseCode, Instant from,
            Instant to) {
        TypedQuery<Object[]> q = em.createQuery("""
                SELECT p.method, COALESCE(SUM(p.amount), 0), COUNT(DISTINCT p.orderId)
                FROM OrderPaymentEntity p, OrderEntity o
                WHERE o.id = p.orderId AND p.status IN ('CAPTURED', 'ON_ACCOUNT') AND
                """ + orderFilter("o", channel, warehouseCode) + " GROUP BY p.method", Object[].class);
        bind(q, channel, warehouseCode, from, to);
        Map<String, SalesAnalytics.PaymentMethodTotal> byMethod = new HashMap<>();
        for (Object[] r : q.getResultList()) {
            byMethod.put((String) r[0], new SalesAnalytics.PaymentMethodTotal((String) r[0], (BigDecimal) r[1],
                    ((Number) r[2]).longValue()));
        }
        // O troco sai do dinheiro: a linha DINHEIRO grava o que foi entregue, não o que ficou.
        SalesAnalytics.PaymentMethodTotal cash = byMethod.get("DINHEIRO");
        if (cash != null) {
            TypedQuery<BigDecimal> change = em.createQuery("""
                    SELECT COALESCE(SUM(o.changeAmount), 0) FROM OrderEntity o
                    WHERE o.changeAmount > 0 AND EXISTS (SELECT 1 FROM OrderPaymentEntity p
                        WHERE p.orderId = o.id AND p.method = 'DINHEIRO' AND p.status = 'CAPTURED') AND
                    """ + orderFilter("o", channel, warehouseCode), BigDecimal.class);
            bind(change, channel, warehouseCode, from, to);
            byMethod.put("DINHEIRO", new SalesAnalytics.PaymentMethodTotal("DINHEIRO",
                    cash.amount().subtract(change.getSingleResult()), cash.orders()));
        }
        return new ArrayList<>(byMethod.values());
    }

    @Override
    public Map<Long, String> sessionOperators(Collection<Long> sessionIds) {
        Map<Long, String> out = new HashMap<>();
        for (List<Long> ids : chunks(sessionIds)) {
            for (Object[] r : em.createQuery("SELECT s.id, s.operator FROM CashRegisterSessionEntity s WHERE s.id IN :ids",
                    Object[].class).setParameter("ids", ids).getResultList()) {
                out.put((Long) r[0], (String) r[1]);
            }
        }
        return out;
    }

    @Override
    public Map<String, Long> operatorUserIds(Collection<String> usernames) {
        Map<String, Long> out = new HashMap<>();
        if (usernames == null || usernames.isEmpty()) {
            return out;
        }
        for (Object[] r : em.createQuery("SELECT u.username, u.id FROM UserEntity u "
                        + "WHERE u.username IN :names AND u.userType = :type", Object[].class)
                .setParameter("names", new ArrayList<>(new LinkedHashSet<>(usernames)))
                .setParameter("type", UserType.OPERATOR).getResultList()) {
            out.put((String) r[0], (Long) r[1]);
        }
        return out;
    }

    @Override
    public List<SalesAnalytics.SlowMover> slowMovers(String warehouseCode, Instant from, Instant to,
            BigDecimal maxSold, int limit) {
        // Saldo positivo no depósito, de produto ativo, publicado e não-kit (pai ou variação). Custo com a
        // mesma precedência do valor de estoque (EST-C022): médio, variação, pai, produto, zero.
        List<Object[]> stock = em.createQuery("""
                SELECT sb.sku, COALESCE(p.name, vp.name, sb.sku), sb.quantity,
                       COALESCE(sb.averageCost, v.costPrice, vp.costPrice, p.costPrice, 0)
                FROM StockBalanceEntity sb
                JOIN WarehouseEntity w ON w.id = sb.warehouseId
                LEFT JOIN ProductVariantEntity v ON v.sku = sb.sku
                LEFT JOIN v.product vp
                LEFT JOIN ProductEntity p ON p.sku = sb.sku
                WHERE w.code = :wh AND sb.quantity > 0
                  AND ((p IS NOT NULL AND p.status = 'ATIVO' AND p.active = true AND p.type <> 'KIT')
                    OR (vp IS NOT NULL AND vp.status = 'ATIVO' AND vp.active = true AND v.active = true
                        AND vp.type <> 'KIT'))
                """, Object[].class).setParameter("wh", warehouseCode).getResultList();
        if (stock.isEmpty()) {
            return List.of();
        }
        List<String> skus = stock.stream().map(r -> (String) r[0]).toList();
        Map<String, BigDecimal> sold = new HashMap<>();
        Map<String, Instant> lastSold = new HashMap<>();
        for (List<String> chunk : chunks(skus)) {
            for (Object[] r : em.createQuery("""
                    SELECT oi.sku, SUM(oi.quantity) FROM OrderItemEntity oi JOIN oi.order o
                    WHERE oi.sku IN :skus AND o.warehouseCode = :wh AND o.status IN :paid
                      AND o.createdAt >= :from AND o.createdAt <= :to
                    GROUP BY oi.sku
                    """, Object[].class).setParameter("skus", chunk).setParameter("wh", warehouseCode)
                    .setParameter("paid", PAID).setParameter("from", from).setParameter("to", to).getResultList()) {
                sold.put((String) r[0], (BigDecimal) r[1]);
            }
            for (Object[] r : em.createQuery("""
                    SELECT oi.sku, MAX(o.createdAt) FROM OrderItemEntity oi JOIN oi.order o
                    WHERE oi.sku IN :skus AND o.warehouseCode = :wh AND o.status IN :paid
                    GROUP BY oi.sku
                    """, Object[].class).setParameter("skus", chunk).setParameter("wh", warehouseCode)
                    .setParameter("paid", PAID).getResultList()) {
                lastSold.put((String) r[0], (Instant) r[1]);
            }
        }
        return stock.stream()
                .map(r -> new SalesAnalytics.SlowMover((String) r[0], (String) r[1], (BigDecimal) r[2],
                        (BigDecimal) r[3], sold.getOrDefault((String) r[0], BigDecimal.ZERO),
                        lastSold.get((String) r[0])))
                .filter(s -> s.quantitySold().compareTo(maxSold) <= 0)
                .sorted(Comparator.comparing(SalesAnalytics.SlowMover::stockValue).reversed()
                        .thenComparing(SalesAnalytics.SlowMover::sku))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<SalesGoal> findGoal(YearMonth month) {
        return Optional.ofNullable(em.find(SalesGoalEntity.class, month.toString()))
                .map(e -> new SalesGoal(YearMonth.parse(e.getMonth()), e.getAmount(), e.getUpdatedBy(),
                        e.getUpdatedAt()));
    }

    @Override
    @Transactional
    public SalesGoal saveGoal(SalesGoal goal) {
        SalesGoalEntity e = Optional.ofNullable(em.find(SalesGoalEntity.class, goal.month().toString()))
                .orElseGet(SalesGoalEntity::new);
        boolean isNew = e.getMonth() == null;
        e.setMonth(goal.month().toString());
        e.setAmount(goal.amount());
        e.setUpdatedBy(goal.updatedBy());
        e.setUpdatedAt(goal.updatedAt());
        if (isNew) {
            em.persist(e);
        }
        return goal;
    }

    private static String orderFilter(String alias, String channel, String warehouseCode) {
        StringBuilder sb = new StringBuilder(alias + ".status IN :paid AND " + alias + ".createdAt >= :from AND "
                + alias + ".createdAt <= :to");
        if (channel != null) {
            sb.append(" AND ").append(alias).append(".channel = :channel");
        }
        if (warehouseCode != null) {
            sb.append(" AND ").append(alias).append(".warehouseCode = :wh");
        }
        return sb.toString();
    }

    private static void bind(TypedQuery<?> q, String channel, String warehouseCode, Instant from, Instant to) {
        q.setParameter("paid", PAID).setParameter("from", from).setParameter("to", to);
        if (channel != null) {
            q.setParameter("channel", channel);
        }
        if (warehouseCode != null) {
            q.setParameter("wh", warehouseCode);
        }
    }

    private static <T> List<List<T>> chunks(Collection<T> values) {
        List<T> all = values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += CHUNK) {
            out.add(all.subList(i, Math.min(all.size(), i + CHUNK)));
        }
        return out;
    }
}
