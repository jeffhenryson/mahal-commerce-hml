package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductRequestEntity;
import com.cernecommerce.adapter.out.persistence.entity.ProductRequestEntryEntity;
import com.cernecommerce.adapter.out.persistence.entity.ProductRequestEventEntity;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestConflictException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.ProductRequest;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntry;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntryStatus;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEvent;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEventType;
import com.cernecommerce.core.domain.model.estoque.ProductRequestNotifyChannel;
import com.cernecommerce.core.domain.model.estoque.ProductRequestSort;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStats;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import com.cernecommerce.core.domain.model.estoque.ProductRequestUrgency;
import com.cernecommerce.core.ports.out.estoque.ProductRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Transactional
public class ProductRequestRepositoryImpl implements ProductRequestRepository {

    private static final Logger log = LoggerFactory.getLogger(ProductRequestRepositoryImpl.class);
    // Mesma escolha do CampaignAutomationRepositoryImpl: não depende do ObjectMapper do contexto.
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ProductRequestUrgency[] URGENCY_BY_RANK = {null, ProductRequestUrgency.LOW,
            ProductRequestUrgency.MEDIUM, ProductRequestUrgency.HIGH};

    private static final String URGENCY_RANK =
            "MAX(CASE e.urgency WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END)";

    private final ProductRequestJpaRepository requests;
    private final ProductRequestEntryJpaRepository entries;
    private final ProductRequestEventJpaRepository events;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductRequestRepositoryImpl(ProductRequestJpaRepository requests, ProductRequestEntryJpaRepository entries,
            ProductRequestEventJpaRepository events) {
        this.requests = requests;
        this.entries = entries;
        this.events = events;
    }

    // ── Item ─────────────────────────────────────────────────────────────────

    @Override
    public ProductRequest save(ProductRequest request) {
        ProductRequestEntity entity;
        if (request.id() == null) {
            entity = new ProductRequestEntity();
        } else {
            entity = requests.findById(request.id())
                    .orElseThrow(() -> new ProductRequestNotFoundException(request.id()));
            if (entity.getVersion() != request.version()) {
                throw new ProductRequestConflictException(request.id());
            }
        }
        entity.setDescription(request.description());
        entity.setBrand(request.brand());
        entity.setVariation(request.variation());
        entity.setCategoryId(request.categoryId());
        entity.setCategoryOther(request.categoryOther());
        entity.setNormalizedKey(request.normalizedKey());
        entity.setStatus(request.status().name());
        entity.setLinkedSku(request.linkedSku());
        entity.setExpectedAt(request.expectedAt());
        entity.setSupplierNote(request.supplierNote());
        entity.setCreatedBy(request.createdBy());
        entity.setCreatedAt(request.createdAt());
        entity.setUpdatedAt(request.updatedAt());
        entity.setClosedAt(request.closedAt());
        try {
            return toDomain(requests.saveAndFlush(entity));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ProductRequestConflictException(request.id());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<ProductRequest> findById(Long id) {
        return requests.findById(id).map(ProductRequestRepositoryImpl::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductRequest> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductRequest> byId = requests.findAllById(ids).stream()
                .map(ProductRequestRepositoryImpl::toDomain)
                .collect(Collectors.toMap(ProductRequest::id, Function.identity()));
        // Na ordem pedida (o top-5 vem ordenado).
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    // ── Solicitações e histórico ─────────────────────────────────────────────

    @Override
    public ProductRequestEntry saveEntry(ProductRequestEntry entry) {
        ProductRequestEntryEntity e = entry.id() == null ? new ProductRequestEntryEntity()
                : entries.findById(entry.id()).orElseThrow(
                        () -> new ProductRequestNotFoundException(entry.requestId(), entry.id()));
        e.setRequestId(entry.requestId());
        e.setCustomerId(entry.customerId());
        e.setCustomerName(entry.customerName());
        e.setCustomerPhone(entry.customerPhone());
        e.setQuantity(entry.quantity());
        e.setMaxPrice(entry.maxPrice());
        e.setUrgency(entry.urgency().name());
        e.setNeededBy(entry.neededBy());
        e.setNote(entry.note());
        e.setStatus(entry.status().name());
        e.setNotifiedAt(entry.notifiedAt());
        e.setNotifiedChannel(entry.notifiedChannel() == null ? null : entry.notifiedChannel().name());
        e.setCreatedBy(entry.createdBy());
        e.setCreatedAt(entry.createdAt());
        return toDomain(entries.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductRequestEntry> findEntries(Long requestId) {
        return entries.findByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
                .map(ProductRequestRepositoryImpl::toDomain).toList();
    }

    @Override
    public ProductRequestEvent saveEvent(ProductRequestEvent event) {
        ProductRequestEventEntity e = new ProductRequestEventEntity();
        e.setRequestId(event.requestId());
        e.setEntryId(event.entryId());
        e.setType(event.type().name());
        e.setFromStatus(event.fromStatus() == null ? null : event.fromStatus().name());
        e.setToStatus(event.toStatus() == null ? null : event.toStatus().name());
        e.setPayload(writeJson(event.payload()));
        e.setActor(event.actor());
        e.setOccurredAt(event.at());
        return toDomain(events.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductRequestEvent> findEvents(Long requestId) {
        return events.findByRequestIdOrderByOccurredAtDescIdDesc(requestId).stream()
                .map(ProductRequestRepositoryImpl::toDomain).toList();
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<Row> search(Collection<ProductRequestStatus> statuses, String normalizedQuery, Long categoryId,
            ProductRequestSort sort, SortDirection direction, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE r.status IN :statuses");
        if (normalizedQuery != null) {
            where.append(" AND r.normalizedKey LIKE :q");
        }
        if (categoryId != null) {
            where.append(" AND r.categoryId = :categoryId");
        }
        String dir = direction == SortDirection.ASC ? " ASC" : " DESC";
        String orderBy = switch (sort) {
            case CREATED_AT -> "r.createdAt";
            case ENTRIES_COUNT -> "COUNT(e.id)";
            case TOTAL_QUANTITY -> "COALESCE(SUM(e.quantity), 0)";
            case MAX_URGENCY -> URGENCY_RANK;
        };
        String jpql = "SELECT r.id FROM ProductRequestEntity r"
                + " LEFT JOIN ProductRequestEntryEntity e ON e.requestId = r.id AND e.status = 'ACTIVE'"
                + where + " GROUP BY r.id, r.createdAt ORDER BY " + orderBy + dir + ", r.id" + dir;
        TypedQuery<Long> idQuery = entityManager.createQuery(jpql, Long.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(r) FROM ProductRequestEntity r" + where, Long.class);
        List<String> statusNames = statuses.stream().map(Enum::name).toList();
        for (TypedQuery<Long> q : List.of(idQuery, countQuery)) {
            q.setParameter("statuses", statusNames);
            if (normalizedQuery != null) {
                q.setParameter("q", "%" + normalizedQuery + "%");
            }
            if (categoryId != null) {
                q.setParameter("categoryId", categoryId);
            }
        }
        List<Long> ids = idQuery.setFirstResult(page * size).setMaxResults(size).getResultList();
        long total = countQuery.getSingleResult();
        Map<Long, ProductRequestStats> stats = statsFor(ids);
        List<Row> rows = findByIds(ids).stream()
                .map(r -> new Row(r, stats.getOrDefault(r.id(), ProductRequestStats.EMPTY)))
                .toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResult<>(rows, page, size, total, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ProductRequestStats> statsFor(Collection<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductRequestStats> out = new HashMap<>();
        for (Object[] row : entries.statsFor(requestIds)) {
            int rank = ((Number) row[3]).intValue();
            out.put((Long) row[0], new ProductRequestStats(
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue(),
                    rank >= 1 && rank <= 3 ? URGENCY_BY_RANK[rank] : null,
                    (LocalDate) row[4],
                    (BigDecimal) row[5],
                    (BigDecimal) row[6],
                    row[7] == null ? 0 : ((Number) row[7]).intValue()));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductRequest> findOpen() {
        return requests.findByStatusIn(ProductRequestStatus.OPEN.stream().map(Enum::name).toList()).stream()
                .map(ProductRequestRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<ProductRequestStatus, Long> countByStatus() {
        Map<ProductRequestStatus, Long> out = new EnumMap<>(ProductRequestStatus.class);
        for (Object[] row : requests.countGroupedByStatus()) {
            out.put(ProductRequestStatus.valueOf((String) row[0]), ((Number) row[1]).longValue());
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public long countArrivedAwaitingNotification() {
        return requests.countArrivedAwaitingNotification();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Row> topRequested(int limit) {
        List<Long> ids = entries.topRequestedIds(PageRequest.of(0, limit));
        Map<Long, ProductRequestStats> stats = statsFor(ids);
        return findByIds(ids).stream()
                .map(r -> new Row(r, stats.getOrDefault(r.id(), ProductRequestStats.EMPTY)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductRequest> findByCustomer(Long customerId) {
        return requests.findByCustomer(customerId).stream().map(ProductRequestRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductRequest> findOrderedByLinkedSkus(Collection<String> skus) {
        if (skus.isEmpty()) {
            return List.of();
        }
        return requests.findByStatusAndLinkedSkuIn(ProductRequestStatus.ORDERED.name(), skus).stream()
                .map(ProductRequestRepositoryImpl::toDomain).toList();
    }

    // ── Mapeamento ───────────────────────────────────────────────────────────

    private static ProductRequest toDomain(ProductRequestEntity e) {
        return new ProductRequest(e.getId(), e.getDescription(), e.getBrand(), e.getVariation(), e.getCategoryId(),
                e.getCategoryOther(), e.getNormalizedKey(), ProductRequestStatus.valueOf(e.getStatus()),
                e.getLinkedSku(), e.getExpectedAt(), e.getSupplierNote(), e.getCreatedBy(), e.getCreatedAt(),
                e.getUpdatedAt(), e.getClosedAt(), e.getVersion());
    }

    private static ProductRequestEntry toDomain(ProductRequestEntryEntity e) {
        return new ProductRequestEntry(e.getId(), e.getRequestId(), e.getCustomerId(), e.getCustomerName(),
                e.getCustomerPhone(), e.getQuantity(), e.getMaxPrice(), ProductRequestUrgency.valueOf(e.getUrgency()),
                e.getNeededBy(), e.getNote(), ProductRequestEntryStatus.valueOf(e.getStatus()), e.getNotifiedAt(),
                e.getNotifiedChannel() == null ? null : ProductRequestNotifyChannel.valueOf(e.getNotifiedChannel()),
                e.getCreatedBy(), e.getCreatedAt());
    }

    private static ProductRequestEvent toDomain(ProductRequestEventEntity e) {
        return new ProductRequestEvent(e.getId(), e.getRequestId(), e.getEntryId(),
                ProductRequestEventType.valueOf(e.getType()),
                e.getFromStatus() == null ? null : ProductRequestStatus.valueOf(e.getFromStatus()),
                e.getToStatus() == null ? null : ProductRequestStatus.valueOf(e.getToStatus()),
                readJson(e.getPayload()), e.getActor(), e.getOccurredAt());
    }

    private static String writeJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("payload do histórico não serializável", ex);
        }
    }

    private static Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            log.warn("product-request.event.payload-invalid json={}", json);
            return null;
        }
    }
}
