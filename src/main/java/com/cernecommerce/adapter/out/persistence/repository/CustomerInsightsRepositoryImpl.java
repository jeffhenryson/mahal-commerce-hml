package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignDispatchStatus;
import com.cernecommerce.core.domain.model.crm.CustomerSegmentRule;
import com.cernecommerce.core.ports.out.crm.CustomerInsightsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@Transactional(readOnly = true)
public class CustomerInsightsRepositoryImpl implements CustomerInsightsRepository {

    /** Mesmo conjunto de OrderJpaRepository.findRevenueTotals. */
    static final List<String> PAID_STATUSES = List.of("PAGO", "SEPARADO", "ENVIADO", "ENTREGUE", "CONCLUIDO",
            "RESERVADO");
    private static final int CHUNK = 1000;

    @PersistenceContext
    private EntityManager em;

    @Override
    public Map<Long, CustomerStats> statsFor(Collection<Long> customerIds) {
        Map<Long, CustomerStats> out = new HashMap<>();
        for (List<Long> ids : chunks(customerIds)) {
            List<Object[]> rows = em.createQuery("""
                    SELECT o.customerId, COALESCE(SUM(o.netAmount), 0), COUNT(o), MAX(o.createdAt)
                    FROM OrderEntity o
                    WHERE o.customerId IN :ids AND o.status IN :paid
                    GROUP BY o.customerId
                    """, Object[].class)
                    .setParameter("ids", ids).setParameter("paid", PAID_STATUSES).getResultList();
            for (Object[] r : rows) {
                out.put((Long) r[0], new CustomerStats((Long) r[0], (BigDecimal) r[1], ((Number) r[2]).longValue(),
                        (Instant) r[3]));
            }
        }
        return out;
    }

    @Override
    public Map<Long, BigDecimal> cashbackAvailableFor(Collection<Long> customerIds, Instant now) {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (List<Long> ids : chunks(customerIds)) {
            List<Object[]> rows = em.createQuery("""
                    SELECT e.customerId, COALESCE(SUM(e.amount), 0) FROM CashbackEntryEntity e
                    WHERE e.customerId IN :ids AND e.availableAt <= :now
                    GROUP BY e.customerId
                    """, Object[].class)
                    .setParameter("ids", ids).setParameter("now", now).getResultList();
            for (Object[] r : rows) {
                out.put((Long) r[0], (BigDecimal) r[1]);
            }
        }
        return out;
    }

    @Override
    public Map<Long, List<String>> tagNamesFor(Collection<Long> customerIds) {
        Map<Long, List<String>> out = new HashMap<>();
        for (List<Long> ids : chunks(customerIds)) {
            List<Object[]> rows = em.createQuery("""
                    SELECT ct.customerId, t.nome FROM CustomerTagEntity ct, TagEntity t
                    WHERE t.id = ct.tagId AND ct.customerId IN :ids
                    ORDER BY t.nome
                    """, Object[].class)
                    .setParameter("ids", ids).getResultList();
            for (Object[] r : rows) {
                out.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((String) r[1]);
            }
        }
        return out;
    }

    @Override
    public BigDecimal averageLtv() {
        List<BigDecimal> sums = em.createQuery("""
                SELECT SUM(o.netAmount) FROM OrderEntity o
                WHERE o.customerId IS NOT NULL AND o.status IN :paid
                GROUP BY o.customerId
                """, BigDecimal.class)
                .setParameter("paid", PAID_STATUSES).getResultList();
        if (sums.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal total = sums.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(sums.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal cashbackAvailableTotal(Instant now) {
        BigDecimal total = em.createQuery("""
                SELECT COALESCE(SUM(e.amount), 0) FROM CashbackEntryEntity e WHERE e.availableAt <= :now
                """, BigDecimal.class)
                .setParameter("now", now).getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    @Override
    public long whatsappDispatchesBetween(Instant from, Instant to) {
        return em.createQuery("""
                SELECT COUNT(l) FROM CampaignLogEntryEntity l, CampaignAutomationEntity a
                WHERE a.id = l.automationId AND a.canal IN :canais AND l.status = :enviado
                  AND l.disparadoEm >= :from AND l.disparadoEm < :to
                """, Long.class)
                .setParameter("canais", List.of(CampaignChannel.WHATSAPP, CampaignChannel.AMBOS))
                .setParameter("enviado", CampaignDispatchStatus.ENVIADO)
                .setParameter("from", from).setParameter("to", to)
                .getSingleResult();
    }

    @Override
    public Map<String, Long> countBySegmento() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] r : em.createQuery("SELECT c.segmento, COUNT(c) FROM CustomerEntity c GROUP BY c.segmento "
                + "ORDER BY c.segmento", Object[].class).getResultList()) {
            out.put((String) r[0], ((Number) r[1]).longValue());
        }
        return out;
    }

    /**
     * Calcula em Java ({@link CustomerSegmentRule}, testável sem banco) e grava em lotes, um UPDATE por
     * segmento. Três leituras agregadas para a base inteira: clientes automáticos, VIPs e pedidos.
     */
    @Override
    @Transactional
    public int recalculateSegments(Long customerId, Instant now) {
        List<Long> ids = customerId == null
                ? em.createQuery("SELECT c.id FROM CustomerEntity c WHERE c.segmentoManual = false", Long.class)
                        .getResultList()
                : em.createQuery("SELECT c.id FROM CustomerEntity c WHERE c.segmentoManual = false AND c.id = :id",
                        Long.class).setParameter("id", customerId).getResultList();
        if (ids.isEmpty()) {
            return 0;
        }
        Set<Long> vips = new HashSet<>();
        for (List<Long> chunk : chunks(ids)) {
            vips.addAll(em.createQuery("""
                    SELECT ct.customerId FROM CustomerTagEntity ct, TagEntity t
                    WHERE t.id = ct.tagId AND UPPER(t.nome) = 'VIP' AND ct.customerId IN :ids
                    """, Long.class).setParameter("ids", chunk).getResultList());
        }
        Map<Long, CustomerStats> stats = statsFor(ids);
        Map<String, List<Long>> bySegment = new HashMap<>();
        for (Long id : ids) {
            CustomerStats s = stats.get(id);
            String segmento = CustomerSegmentRule.segmento(vips.contains(id), s == null ? 0 : s.pedidos(),
                    s == null ? null : s.ultimaCompraEm(), now);
            bySegment.computeIfAbsent(segmento, k -> new ArrayList<>()).add(id);
        }
        int updated = 0;
        for (Map.Entry<String, List<Long>> e : bySegment.entrySet()) {
            for (List<Long> chunk : chunks(e.getValue())) {
                updated += em.createQuery("""
                        UPDATE CustomerEntity c SET c.segmento = :segmento, c.segmentoCalculadoEm = :now
                        WHERE c.id IN :ids AND c.segmentoManual = false
                        """)
                        .setParameter("segmento", e.getKey()).setParameter("now", now)
                        .setParameter("ids", chunk).executeUpdate();
            }
        }
        return updated;
    }

    private static List<List<Long>> chunks(Collection<Long> ids) {
        List<Long> all = ids == null ? List.of() : new ArrayList<>(new java.util.LinkedHashSet<>(ids));
        List<List<Long>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += CHUNK) {
            out.add(all.subList(i, Math.min(all.size(), i + CHUNK)));
        }
        return out;
    }
}
