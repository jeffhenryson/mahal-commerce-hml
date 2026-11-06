package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.FailedEmailEntity;
import com.cernecommerce.core.domain.model.notification.FailedEmail;
import com.cernecommerce.core.ports.out.notification.FailedEmailRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class FailedEmailRepositoryImpl implements FailedEmailRepository {

    private final FailedEmailJpaRepository jpaRepository;

    public FailedEmailRepositoryImpl(FailedEmailJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** Transação própria: o envio pode falhar dentro de uma transação que vai ser desfeita. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailedEmail save(FailedEmail email) {
        FailedEmailEntity entity = new FailedEmailEntity();
        entity.setId(email.id());
        entity.setType(email.type());
        entity.setRecipient(email.recipient());
        entity.setSubject(email.subject());
        entity.setHtml(email.html());
        entity.setError(email.error());
        entity.setCreatedAt(email.createdAt());
        entity.setResendCount(email.resendCount());
        entity.setLastResendAt(email.lastResendAt());
        entity.setLastResendStatus(email.lastResendStatus());
        entity.setLastResendError(email.lastResendError());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FailedEmail> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteCreatedBefore(Instant before) {
        jpaRepository.deleteCreatedBefore(before);
    }

    private FailedEmail toDomain(FailedEmailEntity e) {
        return new FailedEmail(e.getId(), e.getType(), e.getRecipient(), e.getSubject(), e.getHtml(), e.getError(),
                e.getCreatedAt(), e.getResendCount(), e.getLastResendAt(), e.getLastResendStatus(),
                e.getLastResendError());
    }
}
