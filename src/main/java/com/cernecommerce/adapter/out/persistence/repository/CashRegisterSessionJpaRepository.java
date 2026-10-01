package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashRegisterSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

import java.util.Optional;

public interface CashRegisterSessionJpaRepository extends JpaRepository<CashRegisterSessionEntity, Long>,
        JpaSpecificationExecutor<CashRegisterSessionEntity> {

    Optional<CashRegisterSessionEntity> findByOperatorAndStatus(String operator, String status);

    /** Pares {@code [id, operator]} — só as duas colunas, sem carregar a sessão inteira. */
    @Query("SELECT s.id, s.operator FROM CashRegisterSessionEntity s WHERE s.id IN :ids")
    List<Object[]> findOperatorsByIds(@Param("ids") Collection<Long> ids);
}
