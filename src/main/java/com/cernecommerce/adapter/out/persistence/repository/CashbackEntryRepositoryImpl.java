package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashbackEntryEntity;
import com.cernecommerce.adapter.out.persistence.entity.CashbackRedemptionAllocationEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.cashback.CashbackRedemptionAllocation;
import com.cernecommerce.core.domain.model.cashback.RedeemableEarning;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class CashbackEntryRepositoryImpl implements CashbackEntryRepository {

    private final CashbackEntryJpaRepository jpaRepository;
    private final CashbackRedemptionAllocationJpaRepository allocationJpaRepository;

    public CashbackEntryRepositoryImpl(CashbackEntryJpaRepository jpaRepository,
            CashbackRedemptionAllocationJpaRepository allocationJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.allocationJpaRepository = allocationJpaRepository;
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
        return jpaRepository.sumExpiringSoonByCustomerId(customerId, from, to)
                .subtract(jpaRepository.sumConsumedExpiringSoonByCustomerId(customerId, from, to));
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

    @Override
    public void lockCustomer(Long customerId) {
        jpaRepository.lockCustomer(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RedeemableEarning> findRedeemableEarned(Long customerId, Instant now) {
        List<CashbackEntry> live = jpaRepository.findLiveEarnedByCustomerId(customerId, now)
                .stream().map(this::toDomain).toList();
        if (live.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> consumed = sumConsumedByEarnedIds(live.stream().map(CashbackEntry::id).toList());
        List<RedeemableEarning> result = new ArrayList<>();
        for (CashbackEntry e : live) {
            BigDecimal remaining = e.amount().subtract(consumed.getOrDefault(e.id(), BigDecimal.ZERO));
            if (remaining.signum() > 0) {
                result.add(new RedeemableEarning(e, remaining));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> sumConsumedByEarnedIds(Collection<Long> earnedIds) {
        if (earnedIds == null || earnedIds.isEmpty()) {
            return Map.of();
        }
        return toMap(allocationJpaRepository.sumConsumedByEarnedIds(earnedIds));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashbackEntry> findRedeemedByOrderId(Long orderId) {
        return jpaRepository.findFirstByOrderIdAndType(orderId, CashbackEntryType.REDEEMED.name()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumRedemptionReversed(Long redeemedId) {
        return jpaRepository.sumRedemptionReversed(redeemedId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> sumNetAllocationByRedemption(Long redeemedId) {
        return toMap(allocationJpaRepository.sumNetByRedemption(redeemedId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashbackEntry> findAllById(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByIdIn(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public void saveAllocation(CashbackRedemptionAllocation allocation) {
        CashbackRedemptionAllocationEntity entity = new CashbackRedemptionAllocationEntity();
        entity.setEntryId(allocation.entryId());
        entity.setEarnedEntryId(allocation.earnedEntryId());
        entity.setAmount(allocation.amount());
        entity.setCreatedAt(allocation.createdAt());
        allocationJpaRepository.save(entity);
    }

    private static Map<Long, BigDecimal> toMap(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }
        return map;
    }

    private CashbackEntry toDomain(CashbackEntryEntity e) {
        return CashbackEntry.of(e.getId(), e.getCustomerId(), e.getOrderId(), e.getOrderItemId(),
                CashbackEntryType.valueOf(e.getType()), e.getAmount(), e.getAvailableAt(),
                e.getExpiresAt(), e.getReversesEntryId(), e.getCreatedAt());
    }
}
