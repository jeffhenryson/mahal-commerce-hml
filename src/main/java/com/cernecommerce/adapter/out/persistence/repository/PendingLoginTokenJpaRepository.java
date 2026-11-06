package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.PendingLoginTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PendingLoginTokenJpaRepository extends JpaRepository<PendingLoginTokenEntity, Long> {

    Optional<PendingLoginTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update PendingLoginTokenEntity t set t.usedAt = :now where t.tokenHash = :tokenHash and t.usedAt is null")
    int markAsUsedIfAvailable(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from PendingLoginTokenEntity t where t.username = :username")
    void deleteByUsername(@Param("username") String username);

    @Modifying
    @Query("delete from PendingLoginTokenEntity t where t.expiresAt < :before")
    void deleteExpiredBefore(@Param("before") Instant before);
}
