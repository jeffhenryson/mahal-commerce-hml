package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashSessionActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashSessionActivityJpaRepository extends JpaRepository<CashSessionActivityEntity, Long> {

    /** Mais recentes primeiro; {@code id} desempata o mesmo instante (ver CashMovementJpaRepository). */
    List<CashSessionActivityEntity> findBySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);
}
