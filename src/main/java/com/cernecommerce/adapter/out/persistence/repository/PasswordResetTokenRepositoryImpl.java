package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.PasswordResetTokenEntity;
import com.cernecommerce.core.domain.TokenHashUtils;
import com.cernecommerce.core.domain.model.auth.PasswordResetPurpose;
import com.cernecommerce.core.domain.model.auth.PasswordResetToken;
import com.cernecommerce.core.ports.out.notification.PasswordResetTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository jpaRepository;

    public PasswordResetTokenRepositoryImpl(PasswordResetTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PasswordResetToken save(String username, String rawToken, Instant expiresAt, PasswordResetPurpose purpose) {
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setUsername(username);
        entity.setTokenHash(TokenHashUtils.sha256(rawToken));
        entity.setExpiresAt(expiresAt);
        entity.setPurpose(purpose);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> findByToken(String rawToken) {
        return jpaRepository.findByTokenHash(TokenHashUtils.sha256(rawToken)).map(this::toDomain);
    }

    @Override
    public boolean markAsUsed(String rawToken) {
        return jpaRepository.markAsUsedIfNotClaimed(TokenHashUtils.sha256(rawToken), Instant.now()) > 0;
    }

    @Override
    public void deleteByUsername(String username) {
        jpaRepository.deleteByUsername(username);
    }

    @Override
    public void deleteExpiredBefore(Instant before) {
        jpaRepository.deleteExpiredBefore(before);
    }

    private PasswordResetToken toDomain(PasswordResetTokenEntity e) {
        return new PasswordResetToken(e.getId(), e.getUsername(), e.getTokenHash(),
                e.getExpiresAt(), e.getRequestedAt(), e.getUsedAt(), e.getPurpose());
    }
}
