package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashbackEntryEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
@Transactional
public class CashbackEntryRepositoryImpl implements CashbackEntryRepository {

    private final CashbackEntryJpaRepository jpaRepository;

    public CashbackEntryRepositoryImpl(CashbackEntryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CashbackEntry save(CashbackEntry entry) {
        CashbackEntryEntity entity = new CashbackEntryEntity();
        entity.setId(entry.id());
        entity.setCustomerId(entry.customerId());
        entity.setOrderId(entry.orderId());
        entity.setOrderItemId(entry.orderItemId());
        entity.setType(entry.type().name());
        entity.setAmount(entry.amount());
        entity.setAvailableAt(entry.availableAt());
        entity.setExpiresAt(entry.expiresAt());
        entity.setReversesEntryId(entry.reversesEntryId());
        entity.setCreatedAt(entry.createdAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashbackEntry> findByCustomerId(Long customerId, int page, int size) {
        Page<CashbackEntryEntity> result = jpaRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customerId, PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumAvailableByCustomerId(Long customerId, Instant now) {
        return jpaRepository.sumAvailableByCustomerId(customerId, now);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumPendingByCustomerId(Long customerId, Instant now) {
        return jpaRepository.sumPendingByCustomerId(customerId, now);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumExpiringSoonByCustomerId(Long customerId, Instant from, Instant to) {
        return jpaRepository.sumExpiringSoonByCustomerId(customerId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashbackEntry> findEarnedPendingExpiry(Instant now, int limit) {
        return jpaRepository.findEarnedPendingExpiry(now, PageRequest.of(0, limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashbackEntry> findEarnedByOrderId(Long orderId) {
        return jpaRepository.findEarnedByOrderId(orderId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsEarnedForOrder(Long orderId) {
        return jpaRepository.existsByOrderIdAndType(orderId, CashbackEntryType.EARNED.name());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<CashbackEntry> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReversed(Long entryId) {
        return jpaRepository.existsByReversesEntryId(entryId);
    }

    @Override
    public int releasePendingEarned(Long customerId, Instant now) {
        return jpaRepository.releasePendingEarned(customerId, now);
    }

    private CashbackEntry toDomain(CashbackEntryEntity e) {
        return CashbackEntry.of(e.getId(), e.getCustomerId(), e.getOrderId(), e.getOrderItemId(),
                CashbackEntryType.valueOf(e.getType()), e.getAmount(), e.getAvailableAt(),
                e.getExpiresAt(), e.getReversesEntryId(), e.getCreatedAt());
    }
}
