package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.PendingLoginTokenEntity;
import com.cernecommerce.core.domain.TokenHashUtils;
import com.cernecommerce.core.domain.model.auth.PendingLoginPurpose;
import com.cernecommerce.core.domain.model.auth.PendingLoginToken;
import com.cernecommerce.core.ports.out.token.PendingLoginTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class PendingLoginTokenRepositoryImpl implements PendingLoginTokenRepository {

    private final PendingLoginTokenJpaRepository jpaRepository;

    public PendingLoginTokenRepositoryImpl(PendingLoginTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PendingLoginToken save(String username, String rawToken, PendingLoginPurpose purpose, Instant expiresAt) {
        PendingLoginTokenEntity entity = new PendingLoginTokenEntity();
        entity.setUsername(username);
        entity.setTokenHash(TokenHashUtils.sha256(rawToken));
        entity.setPurpose(purpose);
        entity.setExpiresAt(expiresAt);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PendingLoginToken> findByToken(String rawToken) {
        return jpaRepository.findByTokenHash(TokenHashUtils.sha256(rawToken)).map(this::toDomain);
    }

    @Override
    public boolean markAsUsed(String rawToken) {
        return jpaRepository.markAsUsedIfAvailable(TokenHashUtils.sha256(rawToken), Instant.now()) > 0;
    }

    @Override
    public void deleteByUsername(String username) {
        jpaRepository.deleteByUsername(username);
    }

    @Override
    public void deleteExpiredBefore(Instant before) {
        jpaRepository.deleteExpiredBefore(before);
    }

    private PendingLoginToken toDomain(PendingLoginTokenEntity e) {
        return new PendingLoginToken(e.getId(), e.getUsername(), e.getTokenHash(), e.getPurpose(),
                e.getExpiresAt(), e.getCreatedAt(), e.getUsedAt());
    }
}
