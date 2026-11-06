package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashbackRateEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.ports.out.cashback.CashbackRateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class CashbackRateRepositoryImpl implements CashbackRateRepository {

    private final CashbackRateJpaRepository jpaRepository;

    public CashbackRateRepositoryImpl(CashbackRateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CashbackRate save(CashbackRate rate) {
        CashbackRateEntity entity = new CashbackRateEntity();
        entity.setId(rate.id());
        entity.setScope(rate.scope().name());
        entity.setScopeRef(rate.scopeRef());
        entity.setPercent(rate.percent());
        entity.setActive(rate.active());
        entity.setValidFrom(rate.validFrom());
        entity.setValidTo(rate.validTo());
        entity.setCreatedAt(rate.createdAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashbackRate> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashbackRate> findAll(int page, int size) {
        Page<CashbackRateEntity> result = jpaRepository.findAll(PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashbackRate> findApplicable(String sku, String category, Instant at) {
        return jpaRepository.findApplicableCandidates(sku, category, at, PageRequest.of(0, 1))
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashbackRate> findGlobal(Instant at) {
        return jpaRepository.findGlobalCandidates(at, PageRequest.of(0, 1)).stream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveOpenEnded(CashbackScope scope, String scopeRef) {
        return jpaRepository.existsActiveOpenEnded(scope.name(), scopeRef);
    }

    private CashbackRate toDomain(CashbackRateEntity e) {
        return CashbackRate.of(e.getId(), CashbackScope.valueOf(e.getScope()), e.getScopeRef(),
                e.getPercent(), e.isActive(), e.getValidFrom(), e.getValidTo(), e.getCreatedAt());
    }
}
