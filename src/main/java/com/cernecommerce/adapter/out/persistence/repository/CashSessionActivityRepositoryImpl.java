package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashSessionActivityEntity;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivity;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivityType;
import com.cernecommerce.core.ports.out.pdv.CashSessionActivityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class CashSessionActivityRepositoryImpl implements CashSessionActivityRepository {

    private final CashSessionActivityJpaRepository jpaRepository;

    public CashSessionActivityRepositoryImpl(CashSessionActivityJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CashSessionActivity save(CashSessionActivity activity) {
        CashSessionActivityEntity entity = new CashSessionActivityEntity();
        entity.setId(activity.id());
        entity.setSessionId(activity.sessionId());
        entity.setType(activity.type().name());
        entity.setNote(activity.note());
        entity.setUsername(activity.username());
        entity.setCreatedAt(activity.createdAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashSessionActivity> findBySessionId(Long sessionId) {
        return jpaRepository.findBySessionIdOrderByCreatedAtDescIdDesc(sessionId).stream()
                .map(this::toDomain).toList();
    }

    private CashSessionActivity toDomain(CashSessionActivityEntity e) {
        return CashSessionActivity.of(e.getId(), e.getSessionId(), CashSessionActivityType.valueOf(e.getType()),
                e.getNote(), e.getUsername(), e.getCreatedAt());
    }
}
