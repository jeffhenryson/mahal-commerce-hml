package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.FailedEmailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface FailedEmailJpaRepository extends JpaRepository<FailedEmailEntity, Long> {

    @Modifying
    @Query("delete from FailedEmailEntity e where e.createdAt < :before")
    void deleteCreatedBefore(@Param("before") Instant before);
}
