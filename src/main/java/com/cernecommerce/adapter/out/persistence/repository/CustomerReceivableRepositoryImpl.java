package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerEntity;
import com.cernecommerce.adapter.out.persistence.entity.CustomerReceivableEntity;
import com.cernecommerce.adapter.out.persistence.entity.OrderEntity;
import com.cernecommerce.adapter.out.persistence.entity.ReceivableItemEntity;
import com.cernecommerce.adapter.out.persistence.entity.ReceivablePaymentBatchEntity;
import com.cernecommerce.adapter.out.persistence.entity.ReceivablePaymentEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.OnAccountChannel;
import com.cernecommerce.core.domain.model.recebivel.ReceivableFilter;
import com.cernecommerce.core.domain.model.recebivel.ReceivableItem;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePayment;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePaymentBatch;
import com.cernecommerce.core.domain.model.recebivel.ReceivableStatus;
import com.cernecommerce.core.ports.out.recebivel.CustomerReceivableRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional
public class CustomerReceivableRepositoryImpl implements CustomerReceivableRepository {

    private static final List<String> OPEN_STATUSES = List.of(
            ReceivableStatus.ABERTO.name(), ReceivableStatus.PARCIAL.name(), ReceivableStatus.VENCIDO.name());

    private final CustomerReceivableJpaRepository receivableJpa;
    private final ReceivableItemJpaRepository itemJpa;
    private final ReceivablePaymentJpaRepository paymentJpa;
    private final ReceivablePaymentBatchJpaRepository batchJpa;

    public CustomerReceivableRepositoryImpl(CustomerReceivableJpaRepository receivableJpa,
            ReceivableItemJpaRepository itemJpa, ReceivablePaymentJpaRepository paymentJpa,
            ReceivablePaymentBatchJpaRepository batchJpa) {
        this.receivableJpa = receivableJpa;
        this.itemJpa = itemJpa;
        this.paymentJpa = paymentJpa;
        this.batchJpa = batchJpa;
    }

    @Override
    public CustomerReceivable save(CustomerReceivable r) {
        boolean creating = r.id() == null;
        CustomerReceivableEntity entity = creating ? new CustomerReceivableEntity()
                : receivableJpa.findById(r.id()).orElseThrow();
        entity.setCustomerId(r.customerId());
        entity.setOrderId(r.orderId());
        entity.setComandaId(r.comandaId());
        entity.setSessionId(r.sessionId());
        entity.setAmount(r.amount());
        entity.setAmountPaid(r.amountPaid());
        entity.setDueDate(r.dueDate());
        entity.setStatus(r.status().name());
        entity.setCreatedAt(r.createdAt());
        entity.setCreatedBy(r.createdBy());
        entity.setSettledAt(r.settledAt());
        entity.setCancelReason(r.cancelReason());
        entity.setCancelledBy(r.cancelledBy());
        entity.setCancelledAt(r.cancelledAt());
        CustomerReceivableEntity saved = receivableJpa.save(entity);

        List<ReceivableItem> items = r.items();
        if (creating) {
            // Snapshot: gravado uma vez, na criação, e nunca reescrito.
            items = r.items().stream()
                    .map(i -> itemJpa.save(new ReceivableItemEntity(null, saved.getId(), i.orderItemId(), i.sku(),
                            i.productName(), i.quantity(), i.subtotal(), i.mode())))
                    .map(CustomerReceivableRepositoryImpl::toDomain)
                    .toList();
        }
        return toDomain(saved, items);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerReceivable> findById(Long id) {
        return receivableJpa.findById(id).map(e -> withItems(List.of(e)).get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerReceivable> findByOrderId(Long orderId) {
        return receivableJpa.findByOrderId(orderId).map(e -> withItems(List.of(e)).get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerReceivable> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return withItems(receivableJpa.findByOrderIdIn(orderIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerReceivable> findByCustomerId(Long customerId) {
        return withItems(receivableJpa.findByCustomerIdOrderByIdDesc(customerId));
    }

    @Override
    public List<CustomerReceivable> findOpenByCustomerIdForUpdate(Long customerId) {
        return withItems(receivableJpa.findOpenByCustomerIdForUpdate(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CustomerReceivable> findAll(ReceivableFilter filter, LocalDate today, int page, int size) {
        ReceivableFilter f = filter == null ? ReceivableFilter.none() : filter;
        // Specification, e não (:x IS NULL OR ...) em JPQL: com Instant/LocalDate nulos o Postgres
        // recebe o parâmetro como bytea e a consulta quebra (ver OrderRepositoryImpl.findAll).
        Specification<CustomerReceivableEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.customerId() != null) p.add(cb.equal(root.get("customerId"), f.customerId()));
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status().name()));
            if (Boolean.TRUE.equals(f.overdue())) {
                p.add(root.get("status").in(OPEN_STATUSES));
                p.add(cb.lessThan(root.get("dueDate"), today));
            }
            if (f.dueFrom() != null) p.add(cb.greaterThanOrEqualTo(root.get("dueDate"), f.dueFrom()));
            if (f.dueTo() != null) p.add(cb.lessThanOrEqualTo(root.get("dueDate"), f.dueTo()));
            if (f.createdFrom() != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.createdFrom()));
            if (f.createdTo() != null) p.add(cb.lessThanOrEqualTo(root.get("createdAt"), f.createdTo()));
            if (f.channel() == OnAccountChannel.BALCAO) p.add(cb.isNull(root.get("comandaId")));
            if (f.channel() == OnAccountChannel.MESA) p.add(cb.isNotNull(root.get("comandaId")));
            if (f.search() != null && !f.search().isBlank()) {
                // Sem associação JPA para cliente e pedido (as colunas são ids soltos): subconsultas.
                String like = "%" + f.search().trim().toLowerCase(Locale.ROOT) + "%";
                Subquery<Long> customers = query.subquery(Long.class);
                Root<CustomerEntity> c = customers.from(CustomerEntity.class);
                customers.select(c.<Long>get("id")).where(cb.like(cb.lower(c.<String>get("nome")), like));
                Subquery<Long> orders = query.subquery(Long.class);
                Root<OrderEntity> o = orders.from(OrderEntity.class);
                orders.select(o.<Long>get("id")).where(cb.like(cb.lower(o.<String>get("orderNumber")), like));
                p.add(cb.or(root.get("customerId").in(customers), root.get("orderId").in(orders)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<CustomerReceivableEntity> result = receivableJpa.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("id"))));
        return new PageResult<>(withItems(result.getContent()), page, size, result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumOpenBalance(Long customerId) {
        return orZero(receivableJpa.sumOpenBalance(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumOpenBalance(Long customerId, OnAccountChannel channel) {
        return orZero(channel == OnAccountChannel.MESA ? receivableJpa.sumOpenBalanceMesa(customerId)
                : receivableJpa.sumOpenBalanceBalcao(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumOverdueBalance(Long customerId, LocalDate today) {
        return orZero(receivableJpa.sumOverdueBalance(customerId, today));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerBalanceRow> summarizeByCustomer(Collection<ReceivableStatus> statuses, LocalDate today) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        return receivableJpa.summarizeByCustomer(names, today).stream()
                .map(row -> new CustomerBalanceRow((Long) row[0], orZero((BigDecimal) row[1]),
                        orZero((BigDecimal) row[2]), (LocalDate) row[3], ((Number) row[4]).longValue(),
                        orZero((BigDecimal) row[5]), orZero((BigDecimal) row[6])))
                .toList();
    }

    @Override
    public int markOverdue(LocalDate today) {
        return receivableJpa.markOverdue(today);
    }

    @Override
    public ReceivablePaymentBatch saveBatch(ReceivablePaymentBatch b) {
        ReceivablePaymentBatchEntity e = batchJpa.save(new ReceivablePaymentBatchEntity(b.id(), b.customerId(),
                b.cashSessionId(), b.changeAmount(), b.receivedBy(), b.receivedAt()));
        return new ReceivablePaymentBatch(e.getId(), e.getCustomerId(), e.getCashSessionId(), e.getChangeAmount(),
                e.getReceivedBy(), e.getReceivedAt());
    }

    @Override
    public ReceivablePayment savePayment(ReceivablePayment p) {
        return toDomain(paymentJpa.save(new ReceivablePaymentEntity(p.id(), p.receivableId(), p.batchId(),
                p.customerId(), p.amount(), p.method().name(), p.installments(),
                p.channel() == null ? null : p.channel().name(), p.provider() == null ? null : p.provider().name(),
                p.cashSessionId(), p.receivedBy(), p.receivedAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceivablePayment> findPaymentsByReceivableIds(Collection<Long> receivableIds) {
        if (receivableIds == null || receivableIds.isEmpty()) {
            return List.of();
        }
        return paymentJpa.findByReceivableIdInOrderByIdAsc(receivableIds).stream()
                .map(CustomerReceivableRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumReceivedBySessionAndMethod(Long sessionId, PaymentMethod method) {
        return orZero(paymentJpa.sumBySessionAndMethod(sessionId, method.name()));
    }

    // ── Mapeamento ───────────────────────────────────────────────────────────────────────────

    /** Itens de todos os recebíveis da lista numa consulta só. */
    private List<CustomerReceivable> withItems(List<CustomerReceivableEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ReceivableItem>> itemsById = itemJpa.findByReceivableIdInOrderByIdAsc(
                        entities.stream().map(CustomerReceivableEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(ReceivableItemEntity::getReceivableId,
                        Collectors.mapping(CustomerReceivableRepositoryImpl::toDomain, Collectors.toList())));
        return entities.stream().map(e -> toDomain(e, itemsById.getOrDefault(e.getId(), List.of()))).toList();
    }

    private static CustomerReceivable toDomain(CustomerReceivableEntity e, List<ReceivableItem> items) {
        return new CustomerReceivable(e.getId(), e.getCustomerId(), e.getOrderId(), e.getComandaId(),
                e.getSessionId(), e.getAmount(), e.getAmountPaid(), e.getDueDate(),
                ReceivableStatus.valueOf(e.getStatus()), e.getCreatedAt(), e.getCreatedBy(), e.getSettledAt(),
                e.getCancelReason(), e.getCancelledBy(), e.getCancelledAt(), e.getVersion(), items);
    }

    private static ReceivableItem toDomain(ReceivableItemEntity e) {
        return new ReceivableItem(e.getId(), e.getOrderItemId(), e.getSku(), e.getProductName(), e.getQuantity(),
                e.getSubtotal(), e.getMode());
    }

    private static ReceivablePayment toDomain(ReceivablePaymentEntity e) {
        return new ReceivablePayment(e.getId(), e.getReceivableId(), e.getBatchId(), e.getCustomerId(),
                e.getAmount(), PaymentMethod.valueOf(e.getMethod()), e.getInstallments(),
                e.getChannel() == null ? null : PaymentChannel.valueOf(e.getChannel()),
                e.getProvider() == null ? null : PaymentProvider.valueOf(e.getProvider()),
                e.getCashSessionId(), e.getReceivedBy(), e.getReceivedAt());
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
