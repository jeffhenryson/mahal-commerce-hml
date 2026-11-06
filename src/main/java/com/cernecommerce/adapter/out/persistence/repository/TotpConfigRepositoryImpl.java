package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.TotpConfigEntity;
import com.cernecommerce.core.domain.model.auth.TotpConfig;
import com.cernecommerce.core.ports.out.twofa.TotpConfigRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class TotpConfigRepositoryImpl implements TotpConfigRepository {

    private final TotpConfigJpaRepository jpaRepository;

    public TotpConfigRepositoryImpl(TotpConfigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public TotpConfig save(String username, String secretEncrypted) {
        TotpConfigEntity entity = new TotpConfigEntity();
        entity.setUsername(username);
        entity.setSecretEncrypted(secretEncrypted);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TotpConfig> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<String> findEnabledUsernames(java.util.Collection<String> usernames) {
        return usernames.isEmpty() ? java.util.Set.of() : jpaRepository.findEnabledUsernames(usernames);
    }

    @Override
    public void enable(String username, Instant confirmedAt) {
        jpaRepository.enable(username, confirmedAt);
    }

    @Override
    public void deleteByUsername(String username) {
        jpaRepository.deleteByUsername(username);
    }

    @Override
    public void deleteUnconfirmedBefore(Instant before) {
        jpaRepository.deleteUnconfirmedBefore(before);
    }

    private TotpConfig toDomain(TotpConfigEntity e) {
        return new TotpConfig(e.getId(), e.getUsername(), e.getSecretEncrypted(), e.isEnabled(), e.getConfirmedAt());
    }
}
