package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.TotpConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TotpConfigJpaRepository extends JpaRepository<TotpConfigEntity, Long> {
    Optional<TotpConfigEntity> findByUsername(String username);

    @Query("select t.username from TotpConfigEntity t where t.enabled = true and t.username in :usernames")
    java.util.Set<String> findEnabledUsernames(@Param("usernames") java.util.Collection<String> usernames);

    @Modifying
    @Query("update TotpConfigEntity t set t.enabled = true, t.confirmedAt = :confirmedAt where t.username = :username")
    void enable(@Param("username") String username, @Param("confirmedAt") Instant confirmedAt);

    @Modifying
    @Query("delete from TotpConfigEntity t where t.username = :username")
    void deleteByUsername(@Param("username") String username);

    @Modifying
    @Query("delete from TotpConfigEntity t where t.enabled = false and t.createdAt < :before")
    void deleteUnconfirmedBefore(@Param("before") Instant before);
}
