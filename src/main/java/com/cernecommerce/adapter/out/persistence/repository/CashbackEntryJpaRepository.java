package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashbackEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface CashbackEntryJpaRepository extends JpaRepository<CashbackEntryEntity, Long> {

    Page<CashbackEntryEntity> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM CashbackEntryEntity e
            WHERE e.customerId = :customerId AND e.availableAt <= :now
            """)
    BigDecimal sumAvailableByCustomerId(@Param("customerId") Long customerId, @Param("now") Instant now);

    /**
     * Ganhos {@code EARNED} ainda em carência — mesma guarda NOT EXISTS das demais consultas:
     * sem ela, um ganho reembolsado (PDV-F007) ou expirado antes do fim da carência continuaria
     * contando como pendente até a data original de liberação, mesmo já zerado no ledger.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM CashbackEntryEntity e
            WHERE e.customerId = :customerId AND e.type = 'EARNED' AND e.availableAt > :now
              AND NOT EXISTS (SELECT 1 FROM CashbackEntryEntity x WHERE x.reversesEntryId = e.id)
            """)
    BigDecimal sumPendingByCustomerId(@Param("customerId") Long customerId, @Param("now") Instant now);

    /**
     * Ganhos já disponíveis (não em carência) cujo vencimento cai na janela — e que ainda não
     * foram expirados pelo varredor, checado pela ausência de uma entrada referenciando-os via
     * {@code reversesEntryId}.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM CashbackEntryEntity e
            WHERE e.customerId = :customerId AND e.type = 'EARNED'
              AND e.availableAt <= :from AND e.expiresAt BETWEEN :from AND :to
              AND NOT EXISTS (SELECT 1 FROM CashbackEntryEntity x WHERE x.reversesEntryId = e.id)
            """)
    BigDecimal sumExpiringSoonByCustomerId(@Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Ganhos {@code EARNED} vencidos e ainda não expirados, mais antigos primeiro — mesmo padrão
     * de lote e desempate do varredor de reserva de estoque.
     */
    @Query("""
            SELECT e FROM CashbackEntryEntity e
            WHERE e.type = 'EARNED' AND e.expiresAt <= :now
              AND NOT EXISTS (SELECT 1 FROM CashbackEntryEntity x WHERE x.reversesEntryId = e.id)
            ORDER BY e.expiresAt ASC, e.id ASC
            """)
    List<CashbackEntryEntity> findEarnedPendingExpiry(@Param("now") Instant now, Pageable pageable);

    /**
     * Ganhos {@code EARNED} do pedido ainda não revertidos, mesma guarda NOT EXISTS de
     * {@link #findEarnedPendingExpiry} — um por item, por isso sem paginação.
     */
    @Query("""
            SELECT e FROM CashbackEntryEntity e
            WHERE e.type = 'EARNED' AND e.orderId = :orderId
              AND NOT EXISTS (SELECT 1 FROM CashbackEntryEntity x WHERE x.reversesEntryId = e.id)
            ORDER BY e.id ASC
            """)
    List<CashbackEntryEntity> findEarnedByOrderId(@Param("orderId") Long orderId);

    boolean existsByOrderIdAndType(Long orderId, String type);

    /** Já existe um lançamento anulando este (estorno ou expiração)? */
    boolean existsByReversesEntryId(Long reversesEntryId);

    /**
     * Pendência 8 — libera agora os ganhos ainda em carência do cliente (resgate antecipado). Só o
     * {@code availableAt} muda; o vencimento continua o original.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            UPDATE CashbackEntryEntity e SET e.availableAt = :now
            WHERE e.customerId = :customerId AND e.type = 'EARNED' AND e.availableAt > :now
              AND NOT EXISTS (SELECT 1 FROM CashbackEntryEntity x WHERE x.reversesEntryId = e.id)
            """)
    int releasePendingEarned(@Param("customerId") Long customerId, @Param("now") Instant now);
}
